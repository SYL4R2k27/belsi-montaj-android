"""
Полный функционал для кабинета куратора
Версия: 2.0 - с иерархией команд, проверкой фото и массовой постановкой задач
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, Body, File, Form, UploadFile
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime, timedelta, timezone
from typing import List, Optional
from uuid import UUID
from sqlalchemy.orm import Session
from sqlalchemy import text, func, and_, or_

from .db import get_db
from .auth import get_current_user
from .models import (
    User, Shift, ShiftPhoto, Tool, ToolTransaction,
    SupportTicket, SupportMessage, SupportTicketRead, SupportChatRead, Task,
    ForemanInvite, ForemanMembership, ChatThread, ChatParticipant, ChatMessageV2,
    UserProfile,
)

router = APIRouter(prefix="/curator", tags=["curator"])


# ============= Schemas =============

class CuratorInstallerOut(BaseModel):
    """Монтажник в команде бригадира"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    phone: str
    full_name: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    last_activity_at: Optional[datetime] = None
    last_photo_status: str = "none"  # pending/approved/rejected/none
    pending_photos_count: int = 0
    total_shifts: int = 0
    total_hours: float = 0.0


class CuratorForemanOut(BaseModel):
    """Бригадир с вложенной командой монтажников"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    phone: str
    full_name: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    team_size: int = 0
    active_installers_count: int = 0
    total_shifts_today: int = 0
    tools_count: int = 0
    active_tools_issued: int = 0
    pending_photos_count: int = 0
    completion_percentage: float = 0.0  # Процент выполнения задач
    created_at: datetime
    installers: List[CuratorInstallerOut] = []  # Иерархия!


class CuratorPhotoOut(BaseModel):
    """Фото на проверке с информацией о связях"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    user_id: UUID
    user_phone: str
    user_name: Optional[str] = None
    foreman_id: Optional[UUID] = None  # Новое поле!
    foreman_name: Optional[str] = None  # Новое поле!
    photo_url: str
    shift_id: UUID
    timestamp: datetime
    status: str = "pending"
    comment: Optional[str] = None
    category: str = "hourly"  # hourly / problem / question
    ai_comment: Optional[str] = None
    ai_score: Optional[int] = None
    ai_category: Optional[str] = None


class CuratorPhotosResponse(BaseModel):
    """Ответ со списком фото для Android"""
    photos: List[CuratorPhotoOut]


class CuratorDashboardStats(BaseModel):
    """Статистика дашборда куратора"""
    total_installers: int
    active_installers_today: int
    total_foremen: int
    active_foremen_today: int = 0
    total_coordinators: int = 0
    active_coordinators_today: int = 0
    pending_photos: int
    total_shifts_today: int
    total_tools: int
    tools_issued: int
    open_support_tickets: int
    average_completion_percentage: float = 0.0
    total_objects: int = 0
    active_objects: int = 0


class TaskCreateRequest(BaseModel):
    """Запрос на создание задачи"""
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=1000)
    target_user_ids: Optional[List[UUID]] = None  # None = всем!
    deadline: Optional[datetime] = None
    priority: str = Field(..., pattern="^(low|medium|high)$")


class TaskCreateResponse(BaseModel):
    """Ответ после создания задачи"""
    task_id: Optional[UUID] = None  # Для одной задачи
    task_ids: List[UUID] = []  # Для массового создания
    assigned_to_count: int


class PhotoReviewRequest(BaseModel):
    """Запрос на проверку фотографии"""
    status: str = Field(..., pattern="^(approved|rejected)$")
    comment: Optional[str] = Field(None, max_length=500)


# ============= Helper Functions =============

def require_curator(user: User):
    """Проверка что пользователь - куратор"""
    role = (user.role or "").strip().lower()
    if role != "curator":
        raise HTTPException(status_code=403, detail="Curator access required")


def calculate_completion_percentage(db: Session, foreman_id: UUID) -> float:
    """
    Расчет процента выполнения задач для бригадира (одним запросом)
    """
    result = db.execute(text("""
        WITH team AS (
            SELECT installer_user_id AS uid FROM foreman_memberships
            WHERE foreman_user_id = :fid AND status = 'active'
            UNION ALL
            SELECT CAST(:fid AS uuid)
        )
        SELECT
            COUNT(*) AS total,
            COUNT(*) FILTER (WHERE t.status = 'completed') AS done
        FROM tasks t
        WHERE t.assigned_to IN (SELECT uid FROM team)
    """), {"fid": str(foreman_id)}).first()
    if not result or not result[0]:
        return 0.0
    return round((result[1] / result[0]) * 100, 1)


def calculate_completion_percentages_bulk(db: Session, foreman_ids: list) -> dict:
    """
    Bulk version: calculates completion % for ALL foremen in one query.
    Returns dict {foreman_id: percentage}
    """
    if not foreman_ids:
        return {}
    fid_strs = [str(f) for f in foreman_ids]
    rows = db.execute(text("""
        WITH team AS (
            SELECT foreman_user_id AS fid, installer_user_id AS uid
            FROM foreman_memberships WHERE status = 'active'
            AND foreman_user_id = ANY(CAST(:fids AS uuid[]))
            UNION ALL
            SELECT unnest(CAST(:fids AS uuid[])), unnest(CAST(:fids AS uuid[]))
        )
        SELECT
            team.fid,
            COUNT(t.id) AS total,
            COUNT(t.id) FILTER (WHERE t.status = 'completed') AS done
        FROM team
        LEFT JOIN tasks t ON t.assigned_to = team.uid
        GROUP BY team.fid
    """), {"fids": fid_strs}).fetchall()
    result = {}
    for row in rows:
        fid, total, done = row
        result[str(fid)] = round((done / total) * 100, 1) if total else 0.0
    return result


def get_foreman_for_installer(db: Session, installer_id: UUID) -> Optional[tuple]:
    """
    Получить бригадира для монтажника
    Returns: (foreman_id, foreman_name) или None
    """
    query = text("""
        SELECT u.id, u.full_name, u.phone
        FROM foreman_memberships fm
        JOIN users u ON u.id = fm.foreman_user_id
        WHERE fm.installer_user_id = :installer_id
        AND fm.status = 'active'
        LIMIT 1
    """)

    result = db.execute(query, {"installer_id": str(installer_id)}).first()

    if result:
        foreman_id = result[0]
        foreman_name = result[1] or result[2]  # full_name или phone
        return (foreman_id, foreman_name)

    return None


# ============= Endpoints =============

@router.get("/dashboard", response_model=CuratorDashboardStats)
def get_curator_dashboard(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Получить статистику дашборда куратора

    Возвращает общие метрики по системе с процентом выполнения задач
    """
    require_curator(current_user)

    # Подсчет пользователей
    total_installers = db.query(func.count(User.id)).filter(User.role == "installer").scalar() or 0
    total_foremen = db.query(func.count(User.id)).filter(User.role == "foreman").scalar() or 0
    total_coordinators = db.query(func.count(User.id)).filter(User.role == "coordinator").scalar() or 0

    # Активные пользователи сегодня
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)

    active_installers_today = (
        db.query(func.count(func.distinct(Shift.user_id)))
        .join(User, User.id == Shift.user_id)
        .filter(Shift.start_at >= today_start)
        .filter(User.role == "installer")
        .scalar() or 0
    )

    active_foremen_today = (
        db.query(func.count(func.distinct(Shift.user_id)))
        .join(User, User.id == Shift.user_id)
        .filter(Shift.start_at >= today_start)
        .filter(User.role == "foreman")
        .scalar() or 0
    )

    active_coordinators_today = (
        db.query(func.count(func.distinct(Shift.user_id)))
        .join(User, User.id == Shift.user_id)
        .filter(Shift.start_at >= today_start)
        .filter(User.role == "coordinator")
        .scalar() or 0
    )

    # Смены сегодня
    total_shifts_today = (
        db.query(func.count(Shift.id))
        .filter(Shift.start_at >= today_start)
        .scalar() or 0
    )

    # Фотографии на проверке
    pending_photos = (
        db.query(func.count(ShiftPhoto.id))
        .filter(ShiftPhoto.status == "pending")
        .scalar() or 0
    )

    # Инструменты
    total_tools = db.query(func.count(Tool.id)).scalar() or 0
    tools_issued = (
        db.query(func.count(Tool.id))
        .filter(Tool.status == "issued")
        .scalar() or 0
    )

    # Открытые тикеты поддержки
    open_support_tickets = (
        db.query(func.count(SupportTicket.id))
        .filter(SupportTicket.status.in_(["open", "in_progress"]))
        .scalar() or 0
    )

    # Средний процент выполнения задач по всем бригадирам
    try:
        foremen = db.query(User).filter(User.role == "foreman").all()
        if foremen:
            total_percentage = sum(calculate_completion_percentage(db, f.id) for f in foremen)
            average_completion_percentage = round(total_percentage / len(foremen), 1)
        else:
            average_completion_percentage = 0.0
    except Exception:
        average_completion_percentage = 0.0

    # Объекты
    try:
        total_objects_count = db.execute(text("SELECT COUNT(*) FROM site_objects")).scalar() or 0
        active_objects_count = db.execute(text("SELECT COUNT(*) FROM site_objects WHERE status = 'active'")).scalar() or 0
    except Exception:
        total_objects_count = 0
        active_objects_count = 0

    return CuratorDashboardStats(
        total_installers=total_installers,
        active_installers_today=active_installers_today,
        total_foremen=total_foremen,
        active_foremen_today=active_foremen_today,
        total_coordinators=total_coordinators,
        active_coordinators_today=active_coordinators_today,
        pending_photos=pending_photos,
        total_shifts_today=total_shifts_today,
        total_tools=total_tools,
        tools_issued=tools_issued,
        open_support_tickets=open_support_tickets,
        average_completion_percentage=average_completion_percentage,
        total_objects=total_objects_count,
        active_objects=active_objects_count,
    )


@router.get("/foremen", response_model=List[CuratorForemanOut])
def get_foremen_with_hierarchy(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
):
    """
    Получить список бригадиров с вложенными командами (иерархия)

    Каждый бригадир содержит:
    - Свою статистику
    - Процент выполнения задач (0-100)
    - Массив монтажников в команде
    """
    require_curator(current_user)

    # Получаем бригадиров
    foremen = (
        db.query(User)
        .filter(User.role == "foreman")
        .order_by(User.created_at.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )

    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    result = []

    # Bulk: fetch all foreman IDs
    foreman_ids = [f.id for f in foremen]
    fid_strs = [str(f) for f in foreman_ids]

    # Bulk: all memberships
    all_memberships = db.execute(text("""
        SELECT foreman_user_id, installer_user_id
        FROM foreman_memberships
        WHERE foreman_user_id = ANY(CAST(:fids AS uuid[])) AND status = 'active'
    """), {"fids": fid_strs}).fetchall()

    # Build foreman -> installer_ids map
    foreman_teams = {}
    all_installer_ids = set()
    for row in all_memberships:
        fid, iid = str(row[0]), row[1]
        foreman_teams.setdefault(fid, []).append(iid)
        all_installer_ids.add(iid)

    # Bulk: fetch all installer Users
    installer_users = {}
    if all_installer_ids:
        for u in db.query(User).filter(User.id.in_(list(all_installer_ids))).all():
            installer_users[u.id] = u

    # Bulk: installer stats (last_shift, shifts_today, total_shifts, total_hours)
    installer_stats = {}
    if all_installer_ids:
        iid_strs = [str(i) for i in all_installer_ids]
        stats_rows = db.execute(text("""
            SELECT
                s.user_id,
                MAX(s.start_at) AS last_activity,
                COUNT(*) FILTER (WHERE s.start_at >= :today) AS shifts_today,
                COUNT(*) AS total_shifts,
                COALESCE(SUM(s.duration_hours), 0) AS total_hours
            FROM shifts s
            WHERE s.user_id = ANY(CAST(:iids AS uuid[]))
            GROUP BY s.user_id
        """), {"iids": iid_strs, "today": today_start}).fetchall()
        for row in stats_rows:
            installer_stats[str(row[0])] = {
                "last_activity": row[1],
                "shifts_today": row[2],
                "total_shifts": row[3],
                "total_hours": float(row[4]) if row[4] else 0.0,
            }

    # Bulk: pending photos per installer
    installer_pending = {}
    if all_installer_ids:
        iid_strs = [str(i) for i in all_installer_ids]
        pp_rows = db.execute(text("""
            SELECT s.user_id, COUNT(sp.id)
            FROM shift_photos sp
            JOIN shifts s ON s.id = sp.shift_id
            WHERE s.user_id = ANY(CAST(:iids AS uuid[])) AND sp.status = 'pending'
            GROUP BY s.user_id
        """), {"iids": iid_strs}).fetchall()
        for row in pp_rows:
            installer_pending[str(row[0])] = row[1]

    # Bulk: last photo status per installer
    installer_last_photo = {}
    if all_installer_ids:
        iid_strs = [str(i) for i in all_installer_ids]
        lp_rows = db.execute(text("""
            SELECT DISTINCT ON (s.user_id) s.user_id, sp.status
            FROM shift_photos sp
            JOIN shifts s ON s.id = sp.shift_id
            WHERE s.user_id = ANY(CAST(:iids AS uuid[]))
            ORDER BY s.user_id, sp.created_at DESC
        """), {"iids": iid_strs}).fetchall()
        for row in lp_rows:
            installer_last_photo[str(row[0])] = row[1]

    # Bulk: tools per foreman
    tools_data = {}
    if fid_strs:
        tools_rows = db.execute(text("""
            SELECT foreman_id,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE status = 'issued') AS issued
            FROM tools
            WHERE foreman_id = ANY(CAST(:fids AS uuid[]))
            GROUP BY foreman_id
        """), {"fids": fid_strs}).fetchall()
        for row in tools_rows:
            tools_data[str(row[0])] = {"total": row[1], "issued": row[2]}

    # Bulk: completion percentages
    completion_map = calculate_completion_percentages_bulk(db, foreman_ids)

    # Build result
    for foreman in foremen:
        fid_str = str(foreman.id)
        installer_ids = foreman_teams.get(fid_str, [])
        team_size = len(installer_ids)

        installers_list = []
        active_count = 0

        for iid in installer_ids:
            installer = installer_users.get(iid)
            if not installer:
                continue
            iid_str = str(iid)
            stats = installer_stats.get(iid_str, {})
            last_activity_at = stats.get("last_activity")
            if stats.get("shifts_today", 0) > 0:
                active_count += 1

            installers_list.append(
                CuratorInstallerOut(
                    id=installer.id,
                    phone=installer.phone,
                    full_name=getattr(installer, 'full_name', None) or installer.phone,
                    first_name=getattr(installer, 'first_name', None),
                    last_name=getattr(installer, 'last_name', None),
                    last_activity_at=last_activity_at,
                    last_photo_status=installer_last_photo.get(iid_str, "none"),
                    pending_photos_count=installer_pending.get(iid_str, 0),
                    total_shifts=stats.get("total_shifts", 0),
                    total_hours=stats.get("total_hours", 0.0),
                )
            )

        # Team stats
        team_shifts_today = sum(
            installer_stats.get(str(iid), {}).get("shifts_today", 0) for iid in installer_ids
        )
        team_pending = sum(
            installer_pending.get(str(iid), 0) for iid in installer_ids
        )
        tools_info = tools_data.get(fid_str, {})

        result.append(
            CuratorForemanOut(
                id=foreman.id,
                phone=foreman.phone,
                full_name=getattr(foreman, 'full_name', None) or foreman.phone,
                first_name=getattr(foreman, 'first_name', None),
                last_name=getattr(foreman, 'last_name', None),
                team_size=team_size,
                active_installers_count=active_count,
                total_shifts_today=team_shifts_today,
                tools_count=tools_info.get("total", 0),
                active_tools_issued=tools_info.get("issued", 0),
                pending_photos_count=team_pending,
                completion_percentage=completion_map.get(fid_str, 0.0),
                created_at=foreman.created_at,
                installers=installers_list,
            )
        )

    return result


@router.get("/photos", response_model=CuratorPhotosResponse)
def get_photos_for_review(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    limit: int = Query(50, ge=1, le=200),
    user_id: Optional[UUID] = Query(None, description="Фильтр по пользователю"),
    shift_id: Optional[UUID] = Query(None, description="Фильтр по смене"),
    status: Optional[str] = Query(None, description="Фильтр по статусу: pending/approved/rejected/all"),
):
    """
    Получить фото с информацией о связях монтажник→бригадир

    По умолчанию возвращает только pending (обратная совместимость).
    С status=all — все фото, с shift_id — только конкретной смены.
    """
    require_curator(current_user)

    query = (
        db.query(ShiftPhoto)
        .join(Shift, Shift.id == ShiftPhoto.shift_id)
    )

    # Фильтр по статусу — по умолчанию pending (обратная совместимость)
    if status is None or status == "pending":
        query = query.filter(ShiftPhoto.status == "pending")
    elif status != "all":
        query = query.filter(ShiftPhoto.status == status)

    # Фильтр по пользователю
    if user_id:
        query = query.filter(Shift.user_id == user_id)

    # Фильтр по смене
    if shift_id:
        query = query.filter(ShiftPhoto.shift_id == shift_id)

    photos = query.order_by(ShiftPhoto.created_at.desc()).limit(limit).all()

    result = []
    for photo in photos:
        # Получаем смену и пользователя
        shift = db.query(Shift).filter(Shift.id == photo.shift_id).first()
        if not shift:
            continue

        user = db.query(User).filter(User.id == shift.user_id).first()
        if not user:
            continue

        # Получаем бригадира (новая логика!)
        foreman_info = get_foreman_for_installer(db, user.id)
        foreman_id = foreman_info[0] if foreman_info else None
        foreman_name = foreman_info[1] if foreman_info else None

        result.append(
            CuratorPhotoOut(
                id=photo.id,
                user_id=user.id,
                user_phone=user.phone,
                user_name=getattr(user, 'full_name', None) or user.phone,
                foreman_id=foreman_id,  # Новое поле!
                foreman_name=foreman_name,  # Новое поле!
                photo_url=photo.photo_url,
                shift_id=shift.id,
                timestamp=photo.created_at,
                status=photo.status,
                comment=photo.comment,
                category=photo.category or "hourly",
                ai_comment=getattr(photo, "ai_comment", None),
                ai_score=getattr(photo, "ai_score", None),
                ai_category=getattr(photo, "ai_category", "unknown"),
            )
        )

    return CuratorPhotosResponse(photos=result)


@router.post("/photos/{photo_id}/approve")
def approve_photo(
    photo_id: UUID,
    comment: Optional[str] = Body(None, embed=True, max_length=500),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Одобрить фотографию
    """
    require_curator(current_user)

    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    photo.status = "approved"

    if comment:
        curator_name = getattr(current_user, 'full_name', None) or current_user.phone
        new_comment = f"[CURATOR] {curator_name}: {comment}"

        if photo.comment:
            photo.comment = f"{photo.comment}\n{new_comment}"
        else:
            photo.comment = new_comment

    db.commit()

    return {"success": True}


@router.post("/photos/{photo_id}/reject")
def reject_photo(
    photo_id: UUID,
    reason: Optional[str] = Body(None, embed=True, max_length=500),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Отклонить фотографию с указанием причины
    """
    require_curator(current_user)

    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    photo.status = "rejected"

    if reason:
        curator_name = getattr(current_user, 'full_name', None) or current_user.phone
        new_comment = f"[CURATOR] {curator_name}: {reason}"

        if photo.comment:
            photo.comment = f"{photo.comment}\n{new_comment}"
        else:
            photo.comment = new_comment

    db.commit()

    return {"success": True}


class BatchReviewRequest(BaseModel):
    photo_ids: list[str]
    action: str  # "approve" or "reject"
    reason: Optional[str] = None


@router.post("/photos/batch-review")
def batch_review_photos(
    request: BatchReviewRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Массовое одобрение/отклонение фотографий.
    Принимает список photo_ids и действие (approve/reject).
    """
    require_curator(current_user)

    if request.action not in ("approve", "reject"):
        raise HTTPException(status_code=400, detail="action must be 'approve' or 'reject'")

    success_count = 0
    curator_name = getattr(current_user, 'full_name', None) or current_user.phone

    for pid in request.photo_ids:
        photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == pid).first()
        if not photo:
            continue

        photo.status = "approved" if request.action == "approve" else "rejected"

        if request.reason:
            tag = "[CURATOR]" if request.action == "approve" else "[CURATOR]"
            new_comment = f"{tag} {curator_name}: {request.reason}"
            if photo.comment:
                photo.comment = f"{photo.comment}\n{new_comment}"
            else:
                photo.comment = new_comment

        success_count += 1

    db.commit()

    return {"success": True, "processed": success_count, "total": len(request.photo_ids)}


@router.post("/tasks", response_model=TaskCreateResponse)
def create_curator_task(
    task_request: TaskCreateRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Создать задачу для пользователей (точечно или всем)

    Если target_user_ids == null → задача создается для ВСЕХ пользователей
    Если target_user_ids == [id1, id2] → только для выбранных
    """
    require_curator(current_user)

    # Определяем целевых пользователей
    if task_request.target_user_ids is None:
        # Массовая постановка - всем пользователям (foreman + installer)
        target_users = (
            db.query(User)
            .filter(or_(User.role == "foreman", User.role == "installer"))
            .all()
        )
    elif len(task_request.target_user_ids) == 0:
        raise HTTPException(
            status_code=400,
            detail="target_user_ids cannot be empty array. Use null for all users"
        )
    else:
        # Точечная постановка
        target_users = (
            db.query(User)
            .filter(User.id.in_(task_request.target_user_ids))
            .all()
        )

        if len(target_users) != len(task_request.target_user_ids):
            raise HTTPException(status_code=404, detail="Some users not found")

    if not target_users:
        raise HTTPException(status_code=400, detail="No users to assign task to")

    # Создаем задачи
    task_ids = []
    for user in target_users:
        new_task = Task(
            created_by=current_user.id,
            assigned_to=user.id,
            title=task_request.title,
            description=task_request.description,
            priority=task_request.priority,
            status="pending",
            deadline=task_request.deadline,
        )
        db.add(new_task)
        db.flush()
        task_ids.append(new_task.id)

    db.commit()

    # Возвращаем результат
    if len(task_ids) == 1:
        return TaskCreateResponse(
            task_id=task_ids[0],
            task_ids=task_ids,
            assigned_to_count=len(target_users)
        )
    else:
        return TaskCreateResponse(
            task_id=None,
            task_ids=task_ids,
            assigned_to_count=len(target_users)
        )


@router.get("/support")
def get_support_tickets(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    status: Optional[str] = Query(None, description="Фильтр по статусу"),
    limit: int = Query(50, ge=1, le=200),
):
    """
    Получить список тикетов поддержки (чатов с пользователями)

    Уже реализовано в основном коде, этот endpoint для совместимости
    """
    require_curator(current_user)

    query = db.query(SupportTicket).join(User, User.id == SupportTicket.user_id)

    if status:
        query = query.filter(SupportTicket.status == status)

    tickets = query.order_by(SupportTicket.updated_at.desc()).limit(limit).all()

    result = []
    for ticket in tickets:
        user = db.query(User).filter(User.id == ticket.user_id).first()

        last_message = (
            db.query(SupportMessage)
            .filter(SupportMessage.ticket_id == ticket.id)
            .order_by(SupportMessage.created_at.desc())
            .first()
        )

        last_message_snippet = last_message.text[:100] if last_message else "..."

        # Количество непрочитанных
        last_read = (
            db.query(SupportTicketRead)
            .filter(SupportTicketRead.ticket_id == ticket.id)
            .filter(SupportTicketRead.reader_user_id == current_user.id)
            .first()
        )

        if last_read and last_read.last_read_at:
            unread_count = (
                db.query(func.count(SupportMessage.id))
                .filter(SupportMessage.ticket_id == ticket.id)
                .filter(SupportMessage.created_at > last_read.last_read_at)
                .filter(SupportMessage.is_internal == False)
                .scalar() or 0
            )
        else:
            unread_count = (
                db.query(func.count(SupportMessage.id))
                .filter(SupportMessage.ticket_id == ticket.id)
                .filter(SupportMessage.is_internal == False)
                .scalar() or 0
            )

        result.append({
            "id": str(ticket.id),
            "user_id": str(user.id),
            "user_phone": user.phone,
            "user_name": getattr(user, 'full_name', None) or user.phone,
            "last_message_snippet": last_message_snippet,
            "unread_count": unread_count,
            "status": str(ticket.status.value if hasattr(ticket.status, 'value') else ticket.status),
            "category": ticket.category,
            "created_at": ticket.created_at.isoformat(),
            "updated_at": ticket.updated_at.isoformat() if ticket.updated_at else None,
        })

    return result


# ============= Curator Tool Management =============

@router.get("/tools/transactions")
def get_curator_tool_transactions(
    status: Optional[str] = Query(None, description="Filter by status: issued or returned"),
    installer_id: Optional[UUID] = Query(None, description="Filter by installer"),
    page: int = Query(1, ge=1),
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Get all tool transactions (curator access only)
    Supports filtering by status and installer_id, with pagination
    """
    require_curator(current_user)

    from .models import ToolTransaction

    query = db.query(ToolTransaction)

    if status:
        query = query.filter(ToolTransaction.status == status)
    if installer_id:
        query = query.filter(ToolTransaction.installer_id == installer_id)

    total = query.count()
    offset = (page - 1) * limit

    transactions = (
        query.order_by(ToolTransaction.issued_at.desc())
        .offset(offset)
        .limit(limit)
        .all()
    )

    items = []
    for t in transactions:
        items.append({
            "id": str(t.id),
            "tool_id": str(t.tool_id),
            "installer_id": str(t.installer_id),
            "issued_by": str(t.issued_by),
            "issued_at": t.issued_at.isoformat() if t.issued_at else None,
            "issue_comment": t.issue_comment,
            "issue_photo_url": t.issue_photo_url,
            "returned_at": t.returned_at.isoformat() if t.returned_at else None,
            "returned_to": str(t.returned_to) if t.returned_to else None,
            "return_condition": t.return_condition,
            "return_comment": t.return_comment,
            "return_photo_url": t.return_photo_url,
            "status": str(t.status.value) if hasattr(t.status, "value") else str(t.status),
            "created_at": t.created_at.isoformat() if t.created_at else None,
        })

    return {
        "items": items,
        "page": page,
        "total_pages": (total + limit - 1) // limit,
        "total_items": total,
    }


@router.post("/tools/issue")
def curator_issue_tool(
    request: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Curator can issue any tool to any installer
    Body: { "tool_id": "uuid", "installer_id": "uuid", "comment": "optional" }
    """
    require_curator(current_user)

    from .models import ToolTransaction
    from uuid import UUID as PyUUID

    tool_id = request.get("tool_id")
    installer_id = request.get("installer_id")
    comment = request.get("comment")

    if not tool_id or not installer_id:
        raise HTTPException(status_code=400, detail="tool_id and installer_id are required")

    tool = db.query(Tool).filter(Tool.id == tool_id).first()
    if not tool:
        raise HTTPException(status_code=404, detail="Tool not found")

    if tool.status != "available":
        raise HTTPException(status_code=409, detail=f"Tool is not available (status: {tool.status})")

    installer = db.query(User).filter(User.id == installer_id).first()
    if not installer:
        raise HTTPException(status_code=404, detail="Installer not found")

    transaction = ToolTransaction(
        tool_id=tool.id,
        installer_id=installer.id,
        issued_by=current_user.id,
        issued_at=datetime.utcnow(),
        issue_comment=comment,
        status="issued",
    )
    tool.status = "issued"
    db.add(transaction)
    db.commit()
    db.refresh(transaction)

    return {
        "id": str(transaction.id),
        "tool_id": str(transaction.tool_id),
        "installer_id": str(transaction.installer_id),
        "issued_by": str(transaction.issued_by),
        "issued_at": transaction.issued_at.isoformat(),
        "status": "issued",
    }


# ============= Незакрепленные монтажники =============

class UnassignedInstallerOut(BaseModel):
    """Монтажник без бригадира"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    phone: str
    full_name: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    last_activity_at: Optional[datetime] = None
    last_photo_status: str = "none"  # pending, approved, rejected, none
    pending_photos_count: int = 0
    total_shifts: int = 0
    total_hours: float = 0.0
    created_at: datetime


@router.get("/installers/unassigned", response_model=List[UnassignedInstallerOut])
def get_unassigned_installers(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
):
    """
    Получить монтажников без закрепления за бригадиром
    """
    require_curator(current_user)

    # Получаем всех монтажников, которые не в активных memberships
    query = text("""
        SELECT u.id, u.phone, u.full_name, u.first_name, u.last_name, u.created_at
        FROM users u
        WHERE u.role = 'installer'
        AND u.id NOT IN (
            SELECT DISTINCT installer_user_id
            FROM foreman_memberships
            WHERE status = 'active'
        )
        ORDER BY u.created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    rows = db.execute(query, {"limit": limit, "offset": offset}).fetchall()

    result = []
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)

    for row in rows:
        installer_id = row[0]

        # Последняя активность
        last_shift = (
            db.query(Shift)
            .filter(Shift.user_id == installer_id)
            .order_by(Shift.start_at.desc())
            .first()
        )
        last_activity_at = last_shift.start_at if last_shift else None

        # Последнее фото
        last_photo = (
            db.query(ShiftPhoto)
            .join(Shift)
            .filter(Shift.user_id == installer_id)
            .order_by(ShiftPhoto.created_at.desc())
            .first()
        )
        last_photo_status = last_photo.status if last_photo else "none"

        # Pending photos
        pending_photos = (
            db.query(ShiftPhoto)
            .join(Shift)
            .filter(Shift.user_id == installer_id)
            .filter(ShiftPhoto.status == "pending")
            .count()
        )

        # Статистика смен
        total_shifts = db.query(Shift).filter(Shift.user_id == installer_id).count()
        total_hours_query = text("""
            SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (COALESCE(finish_at, NOW()) - start_at)) / 3600), 0)
            FROM shifts
            WHERE user_id = :uid
        """)
        total_hours = db.execute(total_hours_query, {"uid": str(installer_id)}).scalar() or 0.0

        result.append(UnassignedInstallerOut(
            id=row[0],
            phone=row[1],
            full_name=row[2],
            first_name=row[3],
            last_name=row[4],
            created_at=row[5],
            last_activity_at=last_activity_at,
            last_photo_status=last_photo_status,
            pending_photos_count=pending_photos,
            total_shifts=total_shifts,
            total_hours=float(total_hours)
        ))

    return result


# ============= Все пользователи и детальная информация =============

class AllUserOut(BaseModel):
    """Пользователь для общего списка"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    phone: str
    full_name: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    role: str
    foreman_id: Optional[UUID] = None
    foreman_name: Optional[str] = None
    last_activity_at: Optional[datetime] = None
    is_active_today: bool = False
    pending_photos_count: int = 0
    total_shifts: int = 0
    total_hours: float = 0.0
    created_at: datetime


class UserDetailOut(BaseModel):
    """Детальная информация о пользователе"""
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    phone: str
    full_name: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    role: str
    
    # Связи
    foreman_id: Optional[UUID] = None
    foreman_name: Optional[str] = None
    team_members: List[dict] = []  # Для бригадиров - список их монтажников
    
    # Статистика
    total_shifts: int = 0
    total_hours: float = 0.0
    pending_photos_count: int = 0
    approved_photos_count: int = 0
    rejected_photos_count: int = 0
    
    # Задачи
    active_tasks_count: int = 0
    completed_tasks_count: int = 0
    
    # Последняя активность
    last_activity_at: Optional[datetime] = None
    current_shift_id: Optional[UUID] = None
    is_on_shift: bool = False
    current_shift_start_at: Optional[datetime] = None
    current_shift_photos_count: int = 0
    current_shift_elapsed_hours: float = 0.0
    
    # Дополнительные поля для iOS
    total_photos: int = 0
    shift_ended_at: Optional[datetime] = None
    total_pause_duration: Optional[float] = None  # минуты
    total_idle_duration: Optional[float] = None  # минуты
    
    # Текущий статус паузы/простоя
    is_paused: bool = False
    is_idle: bool = False
    # FIX(2026-05-11) BELSI 2.0.0: id текущей паузы нужен фронту для AI-верификации
    # простоя через POST /shift/ai-verify-idle/{pause_id}. Без этого поля кнопка
    # «Проверь простой» на UI куратора не показывается.
    current_pause_id: Optional[UUID] = None
    current_pause_reason: Optional[str] = None
    current_pause_started_at: Optional[datetime] = None
    current_shift_pause_seconds: int = 0
    current_shift_idle_seconds: int = 0
    shift_status: str = 'working'  # working / paused / idle
    
    today_tasks: List[dict] = []
    city: Optional[str] = None
    email: Optional[str] = None
    telegram: Optional[str] = None
    about: Optional[str] = None
    
    created_at: datetime


@router.get("/users/all", response_model=List[AllUserOut])
def get_all_users(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    role: Optional[str] = Query(None, description="Filter by role: foreman, installer, coordinator"),
    limit: int = Query(200, ge=1, le=500),
    offset: int = Query(0, ge=0),
):
    """
    Получить всех пользователей (бригадиров и монтажников)
    С информацией о привязке монтажников к бригадирам
    """
    require_curator(current_user)

    query = db.query(User).filter(or_(User.role == "foreman", User.role == "installer", User.role == "coordinator"))
    
    if role:
        query = query.filter(User.role == role)
    
    users = query.order_by(User.created_at.desc()).offset(offset).limit(limit).all()
    
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    result = []
    
    for user in users:
        # Получаем бригадира для монтажника
        foreman_info = get_foreman_for_installer(db, user.id) if user.role == "installer" else None
        foreman_id = foreman_info[0] if foreman_info else None
        foreman_name = foreman_info[1] if foreman_info else None
        
        # Последняя активность
        last_shift = (
            db.query(Shift)
            .filter(Shift.user_id == user.id)
            .order_by(Shift.start_at.desc())
            .first()
        )
        last_activity_at = last_shift.start_at if last_shift else None
        
        # Активность сегодня
        shift_today = (
            db.query(Shift)
            .filter(Shift.user_id == user.id)
            .filter(Shift.start_at >= today_start)
            .first()
        )
        is_active_today = shift_today is not None
        
        # Pending photos
        pending_photos = (
            db.query(func.count(ShiftPhoto.id))
            .join(Shift)
            .filter(Shift.user_id == user.id)
            .filter(ShiftPhoto.status == "pending")
            .scalar() or 0
        )
        
        # Статистика смен
        total_shifts = db.query(func.count(Shift.id)).filter(Shift.user_id == user.id).scalar() or 0
        total_hours = db.query(func.sum(Shift.duration_hours)).filter(Shift.user_id == user.id).scalar() or 0
        
        result.append(AllUserOut(
            id=user.id,
            phone=user.phone,
            full_name=getattr(user, "full_name", None),
            first_name=getattr(user, "first_name", None),
            last_name=getattr(user, "last_name", None),
            role=user.role or "installer",
            foreman_id=foreman_id,
            foreman_name=foreman_name,
            last_activity_at=last_activity_at,
            is_active_today=is_active_today,
            pending_photos_count=pending_photos,
            total_shifts=total_shifts,
            total_hours=float(total_hours or 0),
            created_at=user.created_at,
        ))
    
    return result


@router.get("/users/{user_id}", response_model=UserDetailOut)
def get_user_detail(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Получить детальную информацию о пользователе
    """
    require_curator(current_user)
    
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    
    # Связи
    foreman_info = get_foreman_for_installer(db, user.id) if user.role == "installer" else None
    foreman_id = foreman_info[0] if foreman_info else None
    foreman_name = foreman_info[1] if foreman_info else None
    
    # Команда бригадира
    team_members = []
    if user.role == "foreman":
        team_query = text("""
            SELECT u.id, u.phone, u.full_name
            FROM foreman_memberships fm
            JOIN users u ON u.id = fm.installer_user_id
            WHERE fm.foreman_user_id = :foreman_id AND fm.status = 'active'
        """)
        rows = db.execute(team_query, {"foreman_id": str(user.id)}).fetchall()
        team_members = [{"id": str(r[0]), "phone": r[1], "name": r[2] or r[1]} for r in rows]
    
    # Статистика смен
    total_shifts = db.query(func.count(Shift.id)).filter(Shift.user_id == user.id).scalar() or 0
    total_hours = db.query(func.sum(Shift.duration_hours)).filter(Shift.user_id == user.id).scalar() or 0
    
    # Статистика фото
    pending_photos = (
        db.query(func.count(ShiftPhoto.id))
        .join(Shift).filter(Shift.user_id == user.id)
        .filter(ShiftPhoto.status == "pending").scalar() or 0
    )
    approved_photos = (
        db.query(func.count(ShiftPhoto.id))
        .join(Shift).filter(Shift.user_id == user.id)
        .filter(ShiftPhoto.status == "approved").scalar() or 0
    )
    rejected_photos = (
        db.query(func.count(ShiftPhoto.id))
        .join(Shift).filter(Shift.user_id == user.id)
        .filter(ShiftPhoto.status == "rejected").scalar() or 0
    )
    
    # Задачи
    active_tasks = (
        db.query(func.count(Task.id))
        .filter(Task.assigned_to == user.id)
        .filter(Task.status.in_(["new", "in_progress"]))
        .scalar() or 0
    )
    completed_tasks = (
        db.query(func.count(Task.id))
        .filter(Task.assigned_to == user.id)
        .filter(Task.status == "done")
        .scalar() or 0
    )
    
    # Последняя активность и текущая смена
    last_shift = (
        db.query(Shift)
        .filter(Shift.user_id == user.id)
        .order_by(Shift.start_at.desc())
        .first()
    )
    last_activity_at = last_shift.start_at if last_shift else None
    
    current_shift = (
        db.query(Shift)
        .filter(Shift.user_id == user.id)
        .filter(Shift.finish_at == None)
        .first()
    )
    
    # Shift detail fields
    shift_start_at = None
    shift_photos_count = 0
    shift_elapsed_hours = 0.0
    if current_shift:
        shift_start_at = current_shift.start_at
        shift_photos_count = (
            db.query(func.count(ShiftPhoto.id))
            .filter(ShiftPhoto.shift_id == current_shift.id)
            .scalar() or 0
        )
        if current_shift.start_at:
            from datetime import timezone
            now_utc = datetime.now(timezone.utc)
            start_aware = current_shift.start_at if current_shift.start_at.tzinfo else current_shift.start_at.replace(tzinfo=timezone.utc)
            delta = now_utc - start_aware
            shift_elapsed_hours = round(delta.total_seconds() / 3600, 2)

    # Дополнительные вычисления для iOS
    total_photos = pending_photos + approved_photos + rejected_photos
    
    # Время окончания последней завершённой смены
    last_finished_shift = (
        db.query(Shift)
        .filter(Shift.user_id == user.id, Shift.finish_at != None)
        .order_by(Shift.finish_at.desc())
        .first()
    )
    shift_ended_at = last_finished_shift.finish_at if last_finished_shift else None
    
    # === Паузы и простои: считаем из ЗАПИСЕЙ shift_pauses (источник правды) ===
    # Логика: reason пустой/NULL = пауза, reason непустой = простой (как в Android)

    # Суммарно по ВСЕМ сменам пользователя
    all_totals = db.execute(
        text("""
            SELECT
                COALESCE(SUM(CASE WHEN (sp.reason IS NULL OR sp.reason = '') THEN sp.duration_seconds ELSE 0 END), 0) AS pause_total,
                COALESCE(SUM(CASE WHEN (sp.reason IS NOT NULL AND sp.reason != '') THEN sp.duration_seconds ELSE 0 END), 0) AS idle_total
            FROM shift_pauses sp
            JOIN shifts s ON s.id = sp.shift_id
            WHERE s.user_id = :uid AND sp.ended_at IS NOT NULL
        """),
        {"uid": str(user.id)}
    ).mappings().first()

    total_pause_secs = int(all_totals["pause_total"]) if all_totals else 0
    total_idle_secs = int(all_totals["idle_total"]) if all_totals else 0
    total_pause_duration = round(float(total_pause_secs) / 60, 1) if total_pause_secs else None
    total_idle_duration = round(float(total_idle_secs) / 60, 1) if total_idle_secs else None

    # Определяем текущий статус паузы/простоя
    is_paused = False
    is_idle = False
    current_pause_id = None  # FIX(2026-05-11) BELSI 2.0.0: для AI verify-idle
    current_pause_reason = None
    current_pause_started_at = None
    current_shift_pause_secs = 0
    current_shift_idle_secs = 0
    shift_status = 'working'

    if current_shift:
        # Текущая смена: считаем из записей shift_pauses (не из колонок!)
        cur_totals = db.execute(
            text("""
                SELECT
                    COALESCE(SUM(CASE WHEN (reason IS NULL OR reason = '') THEN duration_seconds ELSE 0 END), 0) AS pause_total,
                    COALESCE(SUM(CASE WHEN (reason IS NOT NULL AND reason != '') THEN duration_seconds ELSE 0 END), 0) AS idle_total
                FROM shift_pauses
                WHERE shift_id = :sid AND ended_at IS NOT NULL
            """),
            {"sid": str(current_shift.id)}
        ).mappings().first()
        if cur_totals:
            current_shift_pause_secs = int(cur_totals["pause_total"])
            current_shift_idle_secs = int(cur_totals["idle_total"])

        # Проверяем активную паузу
        active_pause = db.execute(
            text('SELECT * FROM shift_pauses WHERE shift_id = :sid AND ended_at IS NULL LIMIT 1'),
            {'sid': str(current_shift.id)}
        ).mappings().first()

        if active_pause:
            current_pause_id = active_pause.get('id')  # FIX(2026-05-11) BELSI 2.0.0
            current_pause_reason = active_pause.get('reason')
            current_pause_started_at = active_pause.get('started_at')

            # Если reason не пустой — это простой (idle), иначе — пауза
            if current_pause_reason and current_pause_reason.strip():
                is_idle = True
                is_paused = False
                shift_status = 'idle'
            else:
                is_paused = True
                is_idle = False
                shift_status = 'paused'
        else:
            shift_status = 'working'
    
    if current_shift:
        current_shift_pause_secs = int(current_shift.pause_seconds or 0)
        current_shift_idle_secs = int(current_shift.idle_seconds or 0)
        
        # Проверяем активную паузу
        active_pause = db.execute(
            text('SELECT * FROM shift_pauses WHERE shift_id = :sid AND ended_at IS NULL LIMIT 1'),
            {'sid': str(current_shift.id)}
        ).mappings().first()
        
        if active_pause:
            is_paused = True
            current_pause_id = active_pause.get('id')  # FIX(2026-05-11) BELSI 2.0.0
            current_pause_reason = active_pause.get('reason')
            current_pause_started_at = active_pause.get('started_at')

            # Проверяем тип: простой или пауза (простой = определённые причины)
            idle_reasons = ['Ожидание материалов', 'Ожидание инструмента', 'Технические проблемы', 'Погодные условия', 'Ожидание бригадира']
            if current_pause_reason and current_pause_reason in idle_reasons:
                is_idle = True
                shift_status = 'idle'
            else:
                shift_status = 'paused'
        else:
            shift_status = 'working'
    
    # Задачи на сегодня
    today_tasks_rows = (
        db.query(Task)
        .filter(Task.assigned_to == user.id)
        .filter(Task.created_at >= today_start)
        .order_by(Task.created_at.desc())
        .all()
    )
    today_tasks_list = [
        {
            "id": str(t.id),
            "title": t.title,
            "status": t.status,
            "created_at": t.created_at.isoformat() if t.created_at else None,
            "completed_at": t.updated_at.isoformat() if t.status == "done" and t.updated_at else None,
        }
        for t in today_tasks_rows
    ]

    return UserDetailOut(
        id=user.id,
        phone=user.phone,
        full_name=getattr(user, "full_name", None),
        first_name=getattr(user, "first_name", None),
        last_name=getattr(user, "last_name", None),
        role=user.role or "installer",
        foreman_id=foreman_id,
        foreman_name=foreman_name,
        team_members=team_members,
        total_shifts=total_shifts,
        total_hours=float(total_hours or 0),
        pending_photos_count=pending_photos,
        approved_photos_count=approved_photos,
        rejected_photos_count=rejected_photos,
        active_tasks_count=active_tasks,
        completed_tasks_count=completed_tasks,
        last_activity_at=last_activity_at,
        current_shift_id=current_shift.id if current_shift else None,
        is_on_shift=current_shift is not None,
        current_shift_start_at=shift_start_at,
        current_shift_photos_count=shift_photos_count,
        current_shift_elapsed_hours=shift_elapsed_hours,
        total_photos=total_photos,
        shift_ended_at=shift_ended_at,
        total_pause_duration=total_pause_duration,
        total_idle_duration=total_idle_duration,
        is_paused=is_paused,
        is_idle=is_idle,
        current_pause_id=current_pause_id,  # FIX(2026-05-11) BELSI 2.0.0
        current_pause_reason=current_pause_reason,
        current_pause_started_at=current_pause_started_at,
        current_shift_pause_seconds=current_shift_pause_secs,
        current_shift_idle_seconds=current_shift_idle_secs,
        shift_status=shift_status,
        today_tasks=today_tasks_list,
        city=getattr(user, "city", None),
        email=getattr(user, "email", None),
        telegram=getattr(user, "telegram", None),
        about=getattr(user, "about", None),
        created_at=user.created_at,
    )


# ============= Change User Role =============

class ChangeRoleRequest(BaseModel):
    role: str  # "installer", "foreman", "curator"


@router.post("/users/{user_id}/role")
def change_user_role(
    user_id: str,
    body: ChangeRoleRequest,
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db),
):
    """Изменить роль пользователя (только для куратора)"""
    require_curator(current_user)

    allowed_roles = ["installer", "foreman", "curator", "coordinator"]
    if body.role not in allowed_roles:
        raise HTTPException(status_code=400, detail=f"Invalid role. Allowed: {allowed_roles}")

    # Find target user
    target_user = db.query(User).filter(User.id == user_id).one_or_none()
    if not target_user:
        raise HTTPException(status_code=404, detail="User not found")

    old_role = target_user.role
    target_user.role = body.role

    # If changing to installer and was foreman, clear their team's foreman_id
    if body.role == "installer" and old_role == "foreman":
        db.query(User).filter(User.foreman_id == target_user.id).update(
            {User.foreman_id: None}, synchronize_session="fetch"
        )

    # If changing from installer to foreman, clear their own foreman_id
    if body.role == "foreman" and old_role == "installer":
        target_user.foreman_id = None

    # Логируем в role_change_log (если таблица существует)
    try:
        db.execute(
            text("""
                INSERT INTO role_change_log (id, user_id, old_role, new_role, changed_by, changed_at)
                VALUES (gen_random_uuid(), :user_id, :old_role, :new_role, :changed_by, NOW())
            """),
            {
                "user_id": str(target_user.id),
                "old_role": old_role,
                "new_role": body.role,
                "changed_by": str(current_user.id),
            }
        )
    except Exception:
        pass  # таблица может ещё не существовать — не блокируем основную логику

    db.commit()

    return {
        "success": True,
        "user_id": str(target_user.id),
        "old_role": old_role,
        "new_role": body.role,
        "message": f"Role changed from {old_role} to {body.role}",
    }


# ==================== СКРИНШОТЫ КУРАТОРА ====================

@router.post("/screenshots")
async def upload_curator_screenshot(
    photo: UploadFile = File(...),
    comment: Optional[str] = Form(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Загрузка скриншота куратора (не требует активной смены)"""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только для кураторов")

    content = await photo.read()
    if not content:
        raise HTTPException(status_code=400, detail="Пустой файл")

    from .storage import save_shift_photo
    try:
        photo_url = await save_shift_photo(content, photo.filename)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Ошибка хранения: {e}")

    # Ищем активную смену, если нет — создаём автоматическую для скриншотов
    shift = db.query(Shift).filter(
        Shift.user_id == current_user.id,
        Shift.status == "active"
    ).order_by(Shift.start_at.desc()).first()

    if shift is None:
        from datetime import timezone as tz
        shift = Shift(
            user_id=current_user.id,
            start_at=datetime.now(tz.utc),
            status="active",
        )
        db.add(shift)
        db.flush()

    now = datetime.utcnow()
    photo_row = ShiftPhoto(
        shift_id=shift.id,
        hour_label="SCREENSHOT",
        photo_url=photo_url,
        status="approved",
        comment=comment or "Автоматический скриншот куратора",
        created_at=now,
    )
    db.add(photo_row)
    db.commit()
    db.refresh(photo_row)

    return {
        "id": str(photo_row.id),
        "photo_url": photo_row.photo_url,
        "created_at": photo_row.created_at.isoformat(),
        "status": photo_row.status,
    }


# ============= DELETE: Управление данными =============


class DeleteUserResponse(BaseModel):
    """Результат удаления пользователя"""
    deleted_user_id: str
    deleted_shifts: int = 0
    deleted_photos: int = 0
    deleted_tasks: int = 0
    deleted_tickets: int = 0
    deleted_invites: int = 0
    deleted_memberships: int = 0
    deleted_messages: int = 0
    deleted_tools: int = 0


@router.delete("/users/{user_id}", response_model=DeleteUserResponse)
def delete_user(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Полное каскадное удаление пользователя и всех его данных.
    Доступно только куратору. Куратор не может удалить сам себя.
    """
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор может удалять пользователей")

    if str(current_user.id) == str(user_id):
        raise HTTPException(status_code=400, detail="Нельзя удалить самого себя")

    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="Пользователь не найден")

    stats = {"deleted_user_id": str(user_id)}

    # 1) shift_photos → через shifts
    shift_ids = [s.id for s in db.query(Shift.id).filter(Shift.user_id == user_id).all()]
    if shift_ids:
        stats["deleted_photos"] = db.query(ShiftPhoto).filter(ShiftPhoto.shift_id.in_(shift_ids)).delete(synchronize_session=False)
        # shift_pauses (raw SQL — модели может не быть)
        db.execute(text("DELETE FROM shift_pauses WHERE shift_id = ANY(:ids)"), {"ids": shift_ids})

    # 2) shifts
    stats["deleted_shifts"] = db.query(Shift).filter(
        or_(Shift.user_id == user_id, Shift.foreman_id == user_id, Shift.curator_id == user_id)
    ).delete(synchronize_session=False)

    # 3) tasks
    stats["deleted_tasks"] = db.query(Task).filter(
        or_(Task.created_by == user_id, Task.assigned_to == user_id)
    ).delete(synchronize_session=False)

    # 4) support: messages → ticket_reads → chat_reads → tickets
    ticket_ids = [t.id for t in db.query(SupportTicket.id).filter(SupportTicket.user_id == user_id).all()]
    if ticket_ids:
        db.query(SupportMessage).filter(SupportMessage.ticket_id.in_(ticket_ids)).delete(synchronize_session=False)
        db.query(SupportTicketRead).filter(SupportTicketRead.ticket_id.in_(ticket_ids)).delete(synchronize_session=False)
        db.query(SupportChatRead).filter(SupportChatRead.ticket_id.in_(ticket_ids)).delete(synchronize_session=False)
    # Читал чужие тикеты
    db.query(SupportTicketRead).filter(SupportTicketRead.reader_user_id == user_id).delete(synchronize_session=False)
    db.query(SupportChatRead).filter(SupportChatRead.reader_user_id == user_id).delete(synchronize_session=False)
    # Сообщения в чужих тикетах
    db.query(SupportMessage).filter(SupportMessage.sender_user_id == user_id).delete(synchronize_session=False)
    stats["deleted_tickets"] = db.query(SupportTicket).filter(SupportTicket.user_id == user_id).delete(synchronize_session=False)

    # 5) foreman_invites
    stats["deleted_invites"] = db.query(ForemanInvite).filter(
        or_(ForemanInvite.foreman_user_id == user_id, ForemanInvite.installer_user_id == user_id)
    ).delete(synchronize_session=False)

    # 6) foreman_memberships
    stats["deleted_memberships"] = db.query(ForemanMembership).filter(
        or_(ForemanMembership.foreman_user_id == user_id, ForemanMembership.installer_user_id == user_id)
    ).delete(synchronize_session=False)

    # 7) chat: messages → participants → threads (если создатель)
    stats["deleted_messages"] = db.query(ChatMessageV2).filter(ChatMessageV2.sender_id == user_id).delete(synchronize_session=False)
    db.query(ChatParticipant).filter(ChatParticipant.user_id == user_id).delete(synchronize_session=False)

    # 8) tools & transactions
    db.query(ToolTransaction).filter(
        or_(ToolTransaction.installer_id == user_id, ToolTransaction.issued_by == user_id, ToolTransaction.returned_to == user_id)
    ).delete(synchronize_session=False)
    # Транзакции для инструментов, принадлежащих бригадиру
    tool_ids = [t.id for t in db.query(Tool.id).filter(Tool.foreman_id == user_id).all()]
    if tool_ids:
        db.query(ToolTransaction).filter(ToolTransaction.tool_id.in_(tool_ids)).delete(synchronize_session=False)
    stats["deleted_tools"] = db.query(Tool).filter(Tool.foreman_id == user_id).delete(synchronize_session=False)

    # 9) team_memberships (raw SQL)
    db.execute(text("DELETE FROM team_memberships WHERE foreman_id = :uid OR installer_id = :uid"), {"uid": str(user_id)})

    # 10) user_profile (CASCADE, но на всякий случай)
    db.query(UserProfile).filter(UserProfile.user_id == user_id).delete(synchronize_session=False)

    # 11) Обнуляем foreman_id у других пользователей
    db.query(User).filter(User.foreman_id == user_id).update({"foreman_id": None}, synchronize_session=False)

    # 12) Обнуляем ссылки в тикетах/сменах (foreman_id, curator_id)
    db.query(SupportTicket).filter(SupportTicket.foreman_id == user_id).update({"foreman_id": None}, synchronize_session=False)
    db.query(SupportTicket).filter(SupportTicket.curator_id == user_id).update({"curator_id": None}, synchronize_session=False)

    # 13) Удаляем пользователя
    db.delete(user)
    db.commit()

    return DeleteUserResponse(**stats)


@router.delete("/tasks/{task_id}")
def delete_task(
    task_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Удалить задачу. Доступно только куратору."""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор может удалять задачи")

    task = db.query(Task).filter(Task.id == task_id).first()
    if not task:
        raise HTTPException(status_code=404, detail="Задача не найдена")

    db.delete(task)
    db.commit()
    return {"status": "ok", "deleted_task_id": str(task_id)}


@router.delete("/shifts/{shift_id}")
def delete_shift(
    shift_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Удалить смену со всеми фотографиями. Доступно только куратору."""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор может удалять смены")

    shift = db.query(Shift).filter(Shift.id == shift_id).first()
    if not shift:
        raise HTTPException(status_code=404, detail="Смена не найдена")

    # Каскадно удаляем фото (через relationship cascade)
    photos_count = db.query(ShiftPhoto).filter(ShiftPhoto.shift_id == shift_id).count()
    db.execute(text("DELETE FROM shift_pauses WHERE shift_id = :sid"), {"sid": str(shift_id)})
    db.delete(shift)
    db.commit()
    return {"status": "ok", "deleted_shift_id": str(shift_id), "deleted_photos": photos_count}


# ==================== ОТЧЁТЫ КООРДИНАТОРА ====================

@router.get("/users/{user_id}/reports")
def get_coordinator_reports(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Получить отчёты координатора. Доступно только куратору."""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор может просматривать отчёты")

    # Проверяем что пользователь существует
    target = db.query(User).filter(User.id == user_id).first()
    if not target:
        raise HTTPException(status_code=404, detail="Пользователь не найден")

    rows = db.execute(
        text("""
            SELECT id, report_date, content, status, photo_urls, curator_feedback, created_at, updated_at
            FROM coordinator_reports
            WHERE coordinator_id = :cid
            ORDER BY created_at DESC
            LIMIT 100
        """),
        {"cid": str(user_id)},
    ).mappings().all()

    result = []
    for r in rows:
        result.append({
            "id": str(r["id"]),
            "report_date": str(r["report_date"]) if r["report_date"] else None,
            "content": r["content"] or "",
            "status": r["status"] or "submitted",
            "photo_urls": r["photo_urls"] or [],
            "curator_feedback": r.get("curator_feedback"),
            "created_at": r["created_at"].isoformat() if r["created_at"] else None,
            "updated_at": r["updated_at"].isoformat() if r["updated_at"] else None,
        })

    return {"reports": result}


@router.put("/users/{user_id}/reports/{report_id}/feedback")
def add_report_feedback(
    user_id: UUID,
    report_id: UUID,
    body: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Дать обратную связь на отчёт координатора. Доступно только куратору."""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор может давать обратную связь")

    feedback = body.get("feedback")
    if not feedback:
        raise HTTPException(status_code=400, detail="feedback is required")

    result = db.execute(
        text("""
            UPDATE coordinator_reports
            SET curator_feedback = :feedback, status = 'reviewed', updated_at = NOW()
            WHERE id = :rid AND coordinator_id = :cid
        """),
        {
            "feedback": feedback,
            "rid": str(report_id),
            "cid": str(user_id),
        },
    )
    db.commit()

    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="Отчёт не найден")

    return {"success": True}


# ==================== ОБЪЕКТЫ КУРАТОРА ====================

@router.get("/objects")
def get_curator_objects(
    status_filter: Optional[str] = Query(None, alias="status", description="active/completed/archived"),
    search: Optional[str] = Query(None),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Все объекты со статистикой (только куратор)"""
    require_curator(current_user)

    from .models import SiteObject, ShiftPhoto, ShiftSegment

    query = db.query(SiteObject)

    if status_filter:
        query = query.filter(SiteObject.status == status_filter)
    else:
        query = query.filter(SiteObject.status != "archived")

    if search:
        pattern = f"%{search}%"
        query = query.filter(
            (SiteObject.name.ilike(pattern)) | (SiteObject.address.ilike(pattern))
        )

    objects = query.order_by(SiteObject.created_at.desc()).offset(offset).limit(limit).all()

    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)
    result = []

    for obj in objects:
        active_workers_count = (
            db.query(func.count(func.distinct(Shift.user_id)))
            .filter(Shift.site_object_id == obj.id, Shift.status == "active")
            .scalar() or 0
        )
        shifts_today = (
            db.query(func.count(Shift.id))
            .filter(Shift.site_object_id == obj.id, Shift.start_at >= today_start)
            .scalar() or 0
        )
        total_photos = (
            db.query(func.count(ShiftPhoto.id))
            .filter(ShiftPhoto.site_object_id == obj.id)
            .scalar() or 0
        )
        coordinator_name = None
        if obj.coordinator_id:
            coord = db.query(User).filter(User.id == obj.coordinator_id).first()
            if coord:
                coordinator_name = coord.full_name

        result.append({
            "id": str(obj.id),
            "name": obj.name,
            "address": obj.address,
            "description": obj.description,
            "status": obj.status or "active",
            "measurements": obj.measurements or {},
            "comments": obj.comments,
            "photo_urls": obj.photo_urls or [],
            "file_urls": obj.file_urls or [],
            "coordinator_id": str(obj.coordinator_id) if obj.coordinator_id else None,
            "coordinator_name": coordinator_name,
            "active_workers_count": active_workers_count,
            "shifts_today": shifts_today,
            "total_photos": total_photos,
            "created_at": obj.created_at.isoformat() if obj.created_at else None,
            "updated_at": obj.updated_at.isoformat() if obj.updated_at else None,
        })

    return result


@router.get("/objects/{object_id}")
def get_curator_object_detail(
    object_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Детальная информация об объекте (только куратор)"""
    require_curator(current_user)

    from .models import SiteObject, ShiftPhoto, ShiftSegment

    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    today_start = datetime.utcnow().replace(hour=0, minute=0, second=0, microsecond=0)

    coordinator_name = None
    if obj.coordinator_id:
        coord = db.query(User).filter(User.id == obj.coordinator_id).first()
        if coord:
            coordinator_name = coord.full_name

    # Active workers
    active_shifts = (
        db.query(Shift).filter(Shift.site_object_id == obj.id, Shift.status == "active").all()
    )
    active_workers = []
    for s in active_shifts:
        user = db.query(User).filter(User.id == s.user_id).first()
        if user:
            active_workers.append({
                "id": str(user.id),
                "name": user.full_name,
                "phone": user.phone,
                "role": user.role or "installer",
                "shift_start": s.start_at.isoformat() if s.start_at else None,
            })

    # Recent photos
    recent_photos = (
        db.query(ShiftPhoto).filter(ShiftPhoto.site_object_id == obj.id)
        .order_by(ShiftPhoto.created_at.desc()).limit(100).all()
    )
    photos_list = []
    for p in recent_photos:
        shift = db.query(Shift).filter(Shift.id == p.shift_id).first()
        user = db.query(User).filter(User.id == shift.user_id).first() if shift else None
        photos_list.append({
            "id": str(p.id),
            "photo_url": p.photo_url,
            "status": p.status,
            "category": p.category or "hourly",
            "comment": p.comment,
            "user_name": user.full_name if user else None,
            "created_at": p.created_at.isoformat() if p.created_at else None,
        })

    # Segments today
    segments = (
        db.query(ShiftSegment).filter(ShiftSegment.site_object_id == obj.id, ShiftSegment.started_at >= today_start)
        .order_by(ShiftSegment.started_at.desc()).limit(50).all()
    )
    segments_list = []
    for seg in segments:
        shift = db.query(Shift).filter(Shift.id == seg.shift_id).first()
        user = db.query(User).filter(User.id == shift.user_id).first() if shift else None
        segments_list.append({
            "id": str(seg.id),
            "worker_name": user.full_name if user else "?",
            "started_at": seg.started_at.isoformat() if seg.started_at else None,
            "ended_at": seg.ended_at.isoformat() if seg.ended_at else None,
        })

    # Reports
    reports_list = []
    try:
        rows = db.execute(
            text("SELECT id, report_date, content, status, photo_urls, curator_feedback, created_at FROM coordinator_reports WHERE site_object_id = :oid ORDER BY created_at DESC LIMIT 50"),
            {"oid": str(obj.id)},
        ).mappings().all()
        for r in rows:
            reports_list.append({
                "id": str(r["id"]),
                "report_date": str(r["report_date"]) if r["report_date"] else None,
                "content": r["content"] or "",
                "status": r["status"] or "submitted",
                "photo_urls": r["photo_urls"] or [],
                "curator_feedback": r.get("curator_feedback"),
                "created_at": r["created_at"].isoformat() if r["created_at"] else None,
            })
    except Exception:
        pass  # coordinator_reports may not have site_object_id yet

    total_shifts = db.query(func.count(Shift.id)).filter(Shift.site_object_id == obj.id).scalar() or 0
    total_photos = db.query(func.count(ShiftPhoto.id)).filter(ShiftPhoto.site_object_id == obj.id).scalar() or 0
    shifts_today = db.query(func.count(Shift.id)).filter(Shift.site_object_id == obj.id, Shift.start_at >= today_start).scalar() or 0

    return {
        "id": str(obj.id),
        "name": obj.name,
        "address": obj.address,
        "description": obj.description,
        "status": obj.status or "active",
        "measurements": obj.measurements or {},
        "comments": obj.comments,
        "photo_urls": obj.photo_urls or [],
        "file_urls": obj.file_urls or [],
        "coordinator_id": str(obj.coordinator_id) if obj.coordinator_id else None,
        "coordinator_name": coordinator_name,
        "active_workers_count": len(active_workers),
        "active_workers": active_workers,
        "recent_photos": photos_list,
        "segments_today": segments_list,
        "reports": reports_list,
        "total_shifts": total_shifts,
        "total_photos": total_photos,
        "shifts_today": shifts_today,
        "created_at": obj.created_at.isoformat() if obj.created_at else None,
        "updated_at": obj.updated_at.isoformat() if obj.updated_at else None,
    }


@router.post("/objects")
def create_curator_object(
    body: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Куратор создаёт объект"""
    require_curator(current_user)

    from .models import SiteObject

    name = body.get("name")
    if not name:
        raise HTTPException(status_code=400, detail="name is required")

    obj = SiteObject(
        name=name,
        address=body.get("address"),
        description=body.get("description"),
        created_by=current_user.id,
        coordinator_id=body.get("coordinator_id"),
    )
    db.add(obj)
    db.commit()
    db.refresh(obj)

    return {"id": str(obj.id), "name": obj.name, "address": obj.address, "status": obj.status}


@router.put("/objects/{object_id}")
def update_curator_object(
    object_id: UUID,
    body: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Куратор обновляет объект"""
    require_curator(current_user)

    from .models import SiteObject

    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    for field in ["name", "address", "description", "measurements", "comments", "status", "photo_urls", "file_urls", "coordinator_id"]:
        if field in body and body[field] is not None:
            setattr(obj, field, body[field])

    db.commit()
    return {"success": True, "id": str(obj.id)}


@router.delete("/objects/{object_id}")
def archive_curator_object(
    object_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Куратор архивирует объект"""
    require_curator(current_user)

    from .models import SiteObject

    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    obj.status = "archived"
    db.commit()
    return {"success": True, "status": "archived"}


# ==================== 3.13 — ИСТОРИЯ ИЗМЕНЕНИЙ РОЛЕЙ ====================

@router.get("/users/{user_id}/role-history")
def get_user_role_history(
    user_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """История изменений роли пользователя (из role_change_log)."""
    require_curator(current_user)

    try:
        rows = db.execute(
            text("""
                SELECT id, old_role, new_role, changed_by, changed_at
                FROM role_change_log
                WHERE user_id = :user_id
                ORDER BY changed_at DESC
                LIMIT 50
            """),
            {"user_id": user_id}
        ).fetchall()

        result = []
        for r in rows:
            # Получаем имя того, кто менял
            changer = db.query(User).filter(User.id == r.changed_by).first() if r.changed_by else None
            result.append({
                "id": str(r.id),
                "old_role": r.old_role,
                "new_role": r.new_role,
                "changed_by": str(r.changed_by) if r.changed_by else None,
                "changed_by_name": changer.full_name if changer else None,
                "changed_at": r.changed_at.isoformat() if r.changed_at else None,
            })

        return {"history": result}
    except Exception as e:
        # Таблица может не существовать
        return {"history": [], "error": str(e)}


# ==================== 3.14 — АНАЛИТИКА С ГРАФИКАМИ ====================

@router.get("/analytics")
def get_curator_analytics(
    period: str = Query("week", regex="^(week|month)$"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Аналитика по дням за неделю или месяц.
    Возвращает: photos_per_day, shifts_per_day, work_hours_per_day, idle_hours_per_day.
    """
    require_curator(current_user)

    days = 7 if period == "week" else 30

    # Фото по дням
    photos_data = db.execute(
        text("""
            SELECT DATE(created_at) as day, COUNT(*) as cnt
            FROM shift_photos
            WHERE created_at >= NOW() - INTERVAL :days
            GROUP BY DATE(created_at)
            ORDER BY day
        """),
        {"days": f"{days} days"}
    ).fetchall()

    # Смены по дням
    shifts_data = db.execute(
        text("""
            SELECT DATE(start_at) as day, COUNT(*) as cnt,
                   COALESCE(SUM(total_seconds), 0) as total_sec,
                   COALESCE(SUM(pause_seconds), 0) + COALESCE(SUM(idle_seconds), 0) as idle_sec
            FROM shifts
            WHERE start_at >= NOW() - INTERVAL :days
            GROUP BY DATE(start_at)
            ORDER BY day
        """),
        {"days": f"{days} days"}
    ).fetchall()

    photos_map = {str(r.day): r.cnt for r in photos_data}
    shifts_map = {}
    work_hours_map = {}
    idle_hours_map = {}
    for r in shifts_data:
        day_str = str(r.day)
        shifts_map[day_str] = r.cnt
        work_hours_map[day_str] = round(r.total_sec / 3600, 1)
        idle_hours_map[day_str] = round(r.idle_sec / 3600, 1)

    # Генерируем массив дней
    from datetime import date, timedelta as td
    today = date.today()
    result_days = []
    for i in range(days - 1, -1, -1):
        d = today - td(days=i)
        ds = str(d)
        result_days.append({
            "date": ds,
            "photos": photos_map.get(ds, 0),
            "shifts": shifts_map.get(ds, 0),
            "work_hours": work_hours_map.get(ds, 0.0),
            "idle_hours": idle_hours_map.get(ds, 0.0),
        })

    return {
        "period": period,
        "days": result_days,
    }


# ========== AI Auto-approve settings ==========

class AutoApproveSettings(BaseModel):
    threshold: int = 0  # 0 = disabled, 80/90/95 = auto-approve score

@router.get("/ai-settings", response_model=AutoApproveSettings)
async def get_ai_settings(db: Session = Depends(get_db), current_user=Depends(get_current_user)):
    if current_user.role != "curator":
        raise HTTPException(403, "Curator only")
    from sqlalchemy import text as sa_text
    row = db.execute(sa_text("SELECT value FROM app_settings WHERE key = 'auto_approve_threshold'")).first()
    threshold = int(row[0]) if row else 0
    return AutoApproveSettings(threshold=threshold)

@router.put("/ai-settings")
async def update_ai_settings(body: AutoApproveSettings, db: Session = Depends(get_db), current_user=Depends(get_current_user)):
    if current_user.role != "curator":
        raise HTTPException(403, "Curator only")
    if body.threshold not in [0, 80, 85, 90, 95]:
        raise HTTPException(400, "Threshold must be 0, 80, 85, 90 or 95")
    from sqlalchemy import text as sa_text
    db.execute(sa_text("INSERT INTO app_settings (key, value) VALUES ('auto_approve_threshold', :v) ON CONFLICT (key) DO UPDATE SET value = :v, updated_at = NOW()"), {"v": str(body.threshold)})
    db.commit()
    return {"status": "ok", "threshold": body.threshold}


# ========== AI Dashboard ==========

class AiDashboardPhotoSummary(BaseModel):
    total_analyzed: int = 0
    auto_approved: int = 0
    needs_attention: int = 0  # score < 60
    avg_score: float = 0.0
    category_counts: dict = {}
    problem_installers: list = []

@router.get("/ai-dashboard")
async def get_ai_dashboard(
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user),
    period: str = Query("today", regex="^(today|week|month)$")
):
    if current_user.role != "curator":
        raise HTTPException(403, "Curator only")
    
    from sqlalchemy import text as sa_text, func as sa_func
    from datetime import timedelta
    
    now = datetime.now(timezone.utc)
    if period == "today":
        since = now.replace(hour=0, minute=0, second=0, microsecond=0)
    elif period == "week":
        since = now - timedelta(days=7)
    else:
        since = now - timedelta(days=30)
    
    # All analyzed photos in period
    photos = db.query(ShiftPhoto).filter(
        ShiftPhoto.ai_analyzed_at != None,
        ShiftPhoto.ai_analyzed_at >= since
    ).all()
    
    total = len(photos)
    if total == 0:
        return AiDashboardPhotoSummary()
    
    scores = [p.ai_score for p in photos if p.ai_score is not None]
    avg = sum(scores) / len(scores) if scores else 0
    
    auto_approved = len([p for p in photos if p.status == "approved" and p.ai_score and p.ai_score >= 80])
    needs_attention = len([p for p in photos if p.ai_score is not None and p.ai_score < 60])
    
    # Category counts
    cats = {}
    for p in photos:
        cat = getattr(p, "ai_category", "unknown") or "unknown"
        cats[cat] = cats.get(cat, 0) + 1
    
    # Problem installers (most problem photos)
    from collections import Counter
    from .models import Shift, User
    
    problem_photos = [p for p in photos if p.ai_score is not None and p.ai_score < 60]
    shift_ids = list(set(p.shift_id for p in problem_photos))
    
    installer_counts = Counter()
    installer_names = {}
    
    if shift_ids:
        shifts = db.query(Shift).filter(Shift.id.in_(shift_ids)).all()
        shift_user_map = {s.id: s.user_id for s in shifts}
        user_ids = list(set(shift_user_map.values()))
        users = db.query(User).filter(User.id.in_(user_ids)).all()
        user_name_map = {u.id: u.full_name or u.phone for u in users}
        
        for p in problem_photos:
            uid = shift_user_map.get(p.shift_id)
            if uid:
                installer_counts[str(uid)] += 1
                installer_names[str(uid)] = user_name_map.get(uid, "?")
    
    top_problem = [
        {"user_id": uid, "name": installer_names.get(uid, "?"), "problem_count": cnt}
        for uid, cnt in installer_counts.most_common(5)
    ]
    
    return AiDashboardPhotoSummary(
        total_analyzed=total,
        auto_approved=auto_approved,
        needs_attention=needs_attention,
        avg_score=round(avg, 1),
        category_counts=cats,
        problem_installers=top_problem
    )


# ============================================================
# AI ENDPOINTS (BELSI 1.3.0 — через XeroCode Gateway)
# FIX(2026-05-10): scope BELSI 1.3.0 AI integration
# ============================================================

class AiSummaryResponse(BaseModel):
    headline: str
    summary: str
    anomalies: list[dict] = []
    recommendations: list[str] = []
    cached: bool = False
    cached_at: Optional[datetime] = None


@router.post("/ai-daily-summary", response_model=AiSummaryResponse)
def ai_daily_summary(
    date: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    AI-сводка дня для куратора (sync, ~5-10 сек).

    1. Кэш на 1 час в ai_analyses.
    2. Если кэша нет — собирает данные за день из БД, шлёт в XeroCode.
    3. Если XeroCode упал — возвращает raw stats без AI-комментария.
    """
    from sqlalchemy import text as sa_text
    import asyncio
    import json
    from .services.xerocode_client import xerocode_client
    from .models import AiAnalysis

    # Только куратор / координатор / chief
    if current_user.role not in ("curator", "coordinator", "production_chief"):
        raise HTTPException(status_code=403, detail="Только куратор/координатор/начальник производства")

    target_date = date or datetime.now(timezone.utc).strftime("%Y-%m-%d")
    request_id = f"belsi-summary-{target_date}-{current_user.role}"

    # Проверка кэша (1 час)
    cached = (
        db.query(AiAnalysis)
        .filter(AiAnalysis.request_id == request_id)
        .first()
    )
    if cached:
        cache_age = datetime.now(timezone.utc) - cached.created_at
        if cache_age < timedelta(hours=1):
            r = cached.result_json or {}
            return AiSummaryResponse(
                headline=r.get("headline", ""),
                summary=r.get("summary", ""),
                anomalies=r.get("anomalies", []),
                recommendations=r.get("recommendations", []),
                cached=True,
                cached_at=cached.created_at,
            )

    # Собираем данные за день
    today_start = datetime.fromisoformat(target_date).replace(tzinfo=timezone.utc)
    today_end = today_start + timedelta(days=1)

    stats = db.execute(sa_text("""
        SELECT
            COUNT(*) FILTER (WHERE status='active') AS active,
            COUNT(*) FILTER (WHERE status='finished') AS finished,
            COALESCE(SUM(GREATEST(total_seconds, 0)), 0) / 3600.0 AS work_hours,
            COALESCE(SUM(idle_seconds), 0) / 3600.0 AS idle_hours,
            COALESCE(SUM(pause_seconds), 0) / 3600.0 AS pause_hours
        FROM shifts
        WHERE start_at >= :start AND start_at < :end
    """), {"start": today_start, "end": today_end}).mappings().first()

    long_pauses = db.execute(sa_text("""
        SELECT COUNT(*) AS cnt FROM shift_pauses p
        JOIN shifts s ON s.id = p.shift_id
        WHERE p.started_at >= :start AND p.started_at < :end
          AND p.duration_seconds > 1800
    """), {"start": today_start, "end": today_end}).mappings().first()

    silent_objects = db.execute(sa_text("""
        SELECT so.name FROM site_objects so
        WHERE NOT EXISTS (
            SELECT 1 FROM shift_photos sp
            WHERE sp.site_object_id = so.id
              AND sp.created_at > NOW() - INTERVAL '4 hours'
        )
        AND EXISTS (
            SELECT 1 FROM shifts s
            WHERE s.site_object_id = so.id
              AND s.status='active'
        )
        LIMIT 5
    """)).mappings().all()

    idle_reasons = db.execute(sa_text("""
        SELECT p.reason, COUNT(*) AS cnt FROM shift_pauses p
        WHERE p.started_at >= :start AND p.started_at < :end
          AND p.reason IS NOT NULL AND p.reason != ''
        GROUP BY p.reason
        ORDER BY cnt DESC
        LIMIT 10
    """), {"start": today_start, "end": today_end}).mappings().all()

    data_for_ai = {
        "date": target_date,
        "active_shifts": int(stats["active"] or 0),
        "finished_shifts": int(stats["finished"] or 0),
        "work_hours": round(float(stats["work_hours"] or 0), 1),
        "idle_hours": round(float(stats["idle_hours"] or 0), 1),
        "pause_hours": round(float(stats["pause_hours"] or 0), 1),
        "long_pauses": int(long_pauses["cnt"] or 0),
        "silent_objects": [r["name"] for r in silent_objects],
        "idle_reasons": [{"reason": r["reason"], "count": int(r["cnt"])} for r in idle_reasons],
    }

    # Вызов XeroCode (sync wrapper)
    envelope = None
    try:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        envelope = loop.run_until_complete(
            xerocode_client.generate(
                prompt_template="daily_summary",
                data=data_for_ai,
                request_id=request_id,
                allow_paid_fallback=False,
            )
        )
        loop.close()
    except Exception as e:
        import logging
        logging.getLogger("ai-daily-summary").warning(f"XeroCode failed: {e}")

    if envelope is None:
        # Fallback: возвращаем raw данные без AI-комментария
        return AiSummaryResponse(
            headline=f"{data_for_ai['active_shifts']} активных смен · {data_for_ai['idle_hours']} ч простоя",
            summary="AI-сводка временно недоступна. Вот сырые данные за день.",
            anomalies=[
                {"type": "raw_stats", "value": data_for_ai}
            ],
            recommendations=[],
            cached=False,
        )

    # Сохраняем в ai_analyses
    result = envelope.get("result", {}) or {}
    meta = envelope.get("meta", {}) or {}
    try:
        analysis = AiAnalysis(
            user_id=current_user.id,
            analysis_type="daily_summary",
            result_json=result,
            result_text=result.get("summary"),
            model_used=meta.get("model_used"),
            provider_used=meta.get("provider_used"),
            tokens_input=meta.get("tokens_input"),
            tokens_output=meta.get("tokens_output"),
            cost_usd=meta.get("cost_usd"),
            duration_ms=meta.get("duration_ms"),
            request_id=request_id,
            paid_fallback_used=meta.get("fallback_used", False),
        )
        db.add(analysis)
        db.commit()
    except Exception as e:
        import logging
        logging.getLogger("ai-daily-summary").warning(f"Failed to save analysis: {e}")
        db.rollback()

    return AiSummaryResponse(
        headline=result.get("headline", ""),
        summary=result.get("summary", ""),
        anomalies=result.get("anomalies", []),
        recommendations=result.get("recommendations", []),
        cached=False,
    )


# ============================================================
# AI Photo Search (NLP-запрос → JSON-фильтр → SQL поиск)
# ============================================================

class PhotoSearchRequest(BaseModel):
    query: str  # NLP-запрос куратора, например "селфи Иванова за неделю"


class PhotoSearchResultItem(BaseModel):
    id: UUID
    photo_url: str
    user_name: Optional[str] = None
    site_object_name: Optional[str] = None
    created_at: datetime
    ai_score: Optional[int] = None
    ai_category: Optional[str] = None
    ai_comment: Optional[str] = None


class PhotoSearchResponse(BaseModel):
    intent_understood: bool
    explanation_ru: str
    filters_applied: dict
    photos: list[PhotoSearchResultItem]
    total_found: int


@router.post("/ai-photo-search", response_model=PhotoSearchResponse)
def ai_photo_search(
    payload: PhotoSearchRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Поиск фото по NLP-запросу через AI.

    1. Шлём query в XeroCode → получаем JSON-фильтры.
    2. Формируем SQL по фильтрам.
    3. Возвращаем результаты + объяснение что AI понял.
    """
    import asyncio
    from .services.xerocode_client import xerocode_client

    if current_user.role not in ("curator", "coordinator", "production_chief"):
        raise HTTPException(status_code=403, detail="Только куратор/координатор/начальник")

    # FIX(2026-05-11) BELSI 2.0.0 (B1): idempotency парсинга query.
    # Один и тот же текст запроса парсится в те же фильтры — нет смысла
    # дёргать AI повторно в течение суток. Реальные фото подтягиваются
    # из БД каждый раз — кэш только на парсинг query → filters.
    import hashlib
    from .models import AiAnalysis
    today_str = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    query_hash = hashlib.sha1(payload.query.strip().lower().encode("utf-8")).hexdigest()[:16]
    request_id = f"belsi-search-{today_str}-{query_hash}"

    cached = db.query(AiAnalysis).filter(AiAnalysis.request_id == request_id).first()
    if cached and cached.result_json:
        parsed = cached.result_json
        envelope = None  # маркер — что результат из кэша
    else:
        # Запрос к AI для парсинга
        try:
            loop = asyncio.new_event_loop()
            asyncio.set_event_loop(loop)
            envelope = loop.run_until_complete(
                xerocode_client.generate(
                    prompt_template="query_to_filter",
                    data={
                        "query": payload.query,
                        "today_iso": today_str,
                    },
                    request_id=request_id,
                    allow_paid_fallback=False,
                )
            )
            loop.close()
        except Exception:
            envelope = None

        if envelope is None and not cached:
            return PhotoSearchResponse(
                intent_understood=False,
                explanation_ru="AI временно недоступен, попробуйте позже",
                filters_applied={},
                photos=[],
                total_found=0,
            )

        parsed = (envelope.get("result", {}) or {}) if envelope else {}

        # Сохраняем в ai_analyses для последующих запросов
        if envelope:
            meta = envelope.get("meta", {}) or {}
            try:
                analysis = AiAnalysis(
                    user_id=current_user.id,
                    analysis_type="photo_search",
                    result_json=parsed,
                    result_text=parsed.get("explanation_ru"),
                    model_used=meta.get("model_used"),
                    provider_used=meta.get("provider_used"),
                    tokens_input=meta.get("tokens_input"),
                    tokens_output=meta.get("tokens_output"),
                    cost_usd=meta.get("cost_usd"),
                    duration_ms=meta.get("duration_ms"),
                    request_id=request_id,
                    paid_fallback_used=meta.get("fallback_used", False),
                )
                db.add(analysis)
                db.commit()
            except Exception:
                db.rollback()

    filters = parsed.get("filters", {}) or {}
    limit = min(int(parsed.get("limit", 20) or 20), 100)
    order_by = parsed.get("order_by", "created_at_desc")

    # Строим SQL с safe-фильтрами (НИКАКИХ raw query от AI!)
    from sqlalchemy import desc, asc
    q = db.query(ShiftPhoto)

    if filters.get("date_from"):
        try:
            q = q.filter(ShiftPhoto.created_at >= datetime.fromisoformat(filters["date_from"]))
        except Exception:
            pass
    if filters.get("date_to"):
        try:
            q = q.filter(ShiftPhoto.created_at < datetime.fromisoformat(filters["date_to"]))
        except Exception:
            pass
    if filters.get("ai_category"):
        q = q.filter(ShiftPhoto.ai_category == filters["ai_category"])
    if filters.get("ai_score_min") is not None:
        q = q.filter(ShiftPhoto.ai_score >= int(filters["ai_score_min"]))
    if filters.get("ai_score_max") is not None:
        q = q.filter(ShiftPhoto.ai_score <= int(filters["ai_score_max"]))
    if filters.get("has_problem"):
        q = q.filter(ShiftPhoto.ai_score < 50)

    if order_by == "ai_score_asc":
        q = q.order_by(asc(ShiftPhoto.ai_score))
    elif order_by == "ai_score_desc":
        q = q.order_by(desc(ShiftPhoto.ai_score))
    else:
        q = q.order_by(desc(ShiftPhoto.created_at))

    total = q.count()
    rows = q.limit(limit).all()

    photos = [
        PhotoSearchResultItem(
            id=p.id,
            photo_url=p.photo_url,
            created_at=p.created_at,
            ai_score=p.ai_score,
            ai_category=p.ai_category,
            ai_comment=p.ai_comment,
        )
        for p in rows
    ]

    return PhotoSearchResponse(
        intent_understood=parsed.get("intent_understood", True),
        explanation_ru=parsed.get("explanation_ru", ""),
        filters_applied=filters,
        photos=photos,
        total_found=total,
    )
