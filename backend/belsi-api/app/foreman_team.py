# app/foreman_team.py
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID as PyUUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import select, func

from .db import get_db
from .models import (
    User, ForemanInvite, ForemanMembership, Shift, ShiftPhoto,
    ChatThread, ChatParticipant, ChatMessageV2,
)
from .auth import get_current_user
from .schemas_foreman_team import ForemanTeamOut, ForemanTeamMemberOut

router = APIRouter(prefix="/foreman/team", tags=["foreman-team"])


@router.get("", response_model=ForemanTeamOut)
def get_team(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    # допускаем только бригадира
    if current_user.role not in ("foreman", "FOREMAN"):
        raise HTTPException(status_code=403, detail="Доступ только для бригадира")

    # Получаем монтажников через memberships
    memberships = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.status == "active"
        )
        .all()
    )

    # Fallback: если memberships пусто, пробуем через инвайты
    if not memberships:
        invites = db.execute(
            select(ForemanInvite)
            .where(ForemanInvite.foreman_phone == current_user.phone)
            .where(ForemanInvite.status == "accepted")
            .where(ForemanInvite.installer_phone.isnot(None))
        ).scalars().all()

        installer_phones = sorted({i.installer_phone for i in invites if i.installer_phone})
        users = []
        if installer_phones:
            users = db.execute(
                select(User).where(User.phone.in_(installer_phones))
            ).scalars().all()

        items = []
        for u in users:
            items.append(_build_member_dto(db, u, None))

        return ForemanTeamOut(items=items, count=len(items))

    # Основной путь: через memberships
    items = []
    for membership in memberships:
        installer = db.query(User).filter(User.id == membership.installer_user_id).first()
        if not installer:
            continue
        items.append(_build_member_dto(db, installer, membership))

    return ForemanTeamOut(items=items, count=len(items))


def _build_member_dto(db: Session, installer: User, membership) -> ForemanTeamMemberOut:
    """Собрать полный DTO для монтажника с его статистикой"""

    # Активная смена
    active_shift = (
        db.query(Shift)
        .filter(Shift.user_id == installer.id, Shift.finish_at.is_(None))
        .first()
    )

    # Последняя смена
    last_shift = (
        db.query(Shift)
        .filter(Shift.user_id == installer.id)
        .order_by(Shift.start_at.desc())
        .first()
    )

    # Последнее фото
    last_photo = (
        db.query(ShiftPhoto)
        .join(Shift)
        .filter(Shift.user_id == installer.id)
        .order_by(ShiftPhoto.created_at.desc())
        .first()
    )

    # Фото на проверке
    pending_photos = (
        db.query(func.count(ShiftPhoto.id))
        .join(Shift)
        .filter(Shift.user_id == installer.id, ShiftPhoto.status == "pending")
        .scalar() or 0
    )

    # Статистика смен
    total_shifts = (
        db.query(func.count(Shift.id))
        .filter(Shift.user_id == installer.id)
        .scalar() or 0
    )
    total_hours = (
        db.query(func.sum(Shift.duration_hours))
        .filter(Shift.user_id == installer.id)
        .scalar() or 0.0
    )

    return ForemanTeamMemberOut(
        id=str(installer.id),
        phone=installer.phone,
        user_id=str(installer.id),
        role=installer.role,
        full_name=getattr(installer, "full_name", None),
        first_name=getattr(installer, "first_name", None),
        last_name=getattr(installer, "last_name", None),
        last_shift_at=last_shift.start_at if last_shift else None,
        active_shift_id=str(active_shift.id) if active_shift else None,
        is_working_now=active_shift is not None,
        last_photo_at=last_photo.created_at if last_photo else None,
        pending_photos_count=pending_photos,
        total_shifts=total_shifts,
        total_hours=float(total_hours),
        joined_at=membership.created_at if membership else None,
    )


@router.get("/{installer_id}")
def get_team_member_detail(
    installer_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Получить детальную информацию о монтажнике из своей команды.
    Бригадир видит: статистику, смены, фото, паузы.
    """
    if current_user.role not in ("foreman", "FOREMAN"):
        raise HTTPException(status_code=403, detail="Доступ только для бригадира")

    from uuid import UUID as PyUUID
    try:
        uid = PyUUID(installer_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid installer_id")

    # Проверяем, что монтажник в команде бригадира
    membership = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.installer_user_id == uid,
            ForemanMembership.status == "active"
        )
        .first()
    )
    if not membership:
        raise HTTPException(status_code=404, detail="Монтажник не найден в вашей команде")

    installer = db.query(User).filter(User.id == uid).first()
    if not installer:
        raise HTTPException(status_code=404, detail="Пользователь не найден")

    member_dto = _build_member_dto(db, installer, membership)

    # Дополнительно: список фото монтажника
    from .models import ShiftPhoto, Shift
    photos = (
        db.query(ShiftPhoto)
        .join(Shift)
        .filter(Shift.user_id == uid)
        .order_by(ShiftPhoto.created_at.desc())
        .limit(50)
        .all()
    )

    photos_list = []
    for p in photos:
        photos_list.append({
            "id": str(p.id),
            "photo_url": p.photo_url if hasattr(p, "photo_url") else None,
            "status": p.status,
            "created_at": p.created_at.isoformat() if p.created_at else None,
            "comment": getattr(p, "comment", None),
            "hour_label": getattr(p, "hour_label", None),
        })

    # Список смен
    shifts = (
        db.query(Shift)
        .filter(Shift.user_id == uid)
        .order_by(Shift.start_at.desc())
        .limit(20)
        .all()
    )

    shifts_list = []
    for s in shifts:
        shifts_list.append({
            "id": str(s.id),
            "start_at": s.start_at.isoformat() if s.start_at else None,
            "finish_at": s.finish_at.isoformat() if s.finish_at else None,
            "duration_hours": float(s.duration_hours) if s.duration_hours else None,
            "status": "active" if s.finish_at is None else "completed",
        })

    return {
        "member": member_dto.model_dump(),
        "photos": photos_list,
        "shifts": shifts_list,
    }


# =====================================================
# 3.10 — Group thread with entire team
# =====================================================

@router.post("/group-thread")
def get_or_create_group_thread(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Найти или создать групповой чат со всей бригадой.
    Бригадир + все его активные монтажники.
    Возвращает thread_id для навигации в чат.
    """
    if current_user.role not in ("foreman", "FOREMAN"):
        raise HTTPException(status_code=403, detail="Доступ только для бригадира")

    # Получаем всех монтажников бригады
    memberships = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.status == "active"
        )
        .all()
    )
    installer_ids = [m.installer_user_id for m in memberships]

    if not installer_ids:
        raise HTTPException(status_code=400, detail="В вашей бригаде нет монтажников")

    all_member_ids = set(installer_ids) | {current_user.id}

    # Ищем существующий групповой тред с tag "team_<foreman_id>"
    team_tag = f"team_{current_user.id}"

    # Поиск по имени тега в группах, где бригадир — участник
    existing_threads = (
        db.query(ChatThread)
        .join(ChatParticipant, ChatParticipant.thread_id == ChatThread.id)
        .filter(
            ChatThread.type == "group",
            ChatThread.name.like(f"%{team_tag}%"),
            ChatParticipant.user_id == current_user.id,
        )
        .all()
    )

    for t in existing_threads:
        # Убеждаемся, что бригадир является участником
        return {"thread_id": str(t.id)}

    # Создаём новый групповой тред
    now = datetime.now(timezone.utc)
    foreman_name = current_user.full_name or current_user.phone

    thread = ChatThread(
        type="group",
        name=f"Бригада {foreman_name} [{team_tag}]",
        created_by=current_user.id,
        created_at=now,
        updated_at=now,
    )
    db.add(thread)
    db.flush()

    # Добавляем бригадира как admin
    db.add(ChatParticipant(
        thread_id=thread.id,
        user_id=current_user.id,
        role="admin",
        joined_at=now,
    ))

    # Добавляем монтажников
    for installer_id in installer_ids:
        db.add(ChatParticipant(
            thread_id=thread.id,
            user_id=installer_id,
            role="member",
            joined_at=now,
        ))

    # Системное сообщение
    db.add(ChatMessageV2(
        thread_id=thread.id,
        sender_id=current_user.id,
        message_type="system",
        text="Групповой чат бригады создан",
        created_at=now,
    ))

    db.commit()
    return {"thread_id": str(thread.id)}


# =====================================================
# 3.11 — Pause/idle statistics for installer
# =====================================================

class PauseStatsOut(BaseModel):
    installer_id: str
    installer_name: Optional[str] = None
    total_shifts: int = 0
    total_pause_seconds: int = 0
    total_idle_seconds: int = 0
    total_work_seconds: int = 0
    avg_pause_per_shift: float = 0.0
    avg_idle_per_shift: float = 0.0
    idle_percentage: float = 0.0  # % от рабочего времени


@router.get("/{installer_id}/pause-stats", response_model=PauseStatsOut)
def get_pause_stats(
    installer_id: str,
    from_date: Optional[str] = Query(None, alias="from"),
    to_date: Optional[str] = Query(None, alias="to"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Статистика простоев монтажника: паузы, idle, % простоя.
    Фильтр по дате: ?from=2024-01-01&to=2024-12-31
    """
    if current_user.role not in ("foreman", "FOREMAN"):
        raise HTTPException(status_code=403, detail="Доступ только для бригадира")

    try:
        uid = PyUUID(installer_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid installer_id")

    # Проверяем что монтажник в команде
    membership = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.installer_user_id == uid,
            ForemanMembership.status == "active"
        )
        .first()
    )
    if not membership:
        raise HTTPException(status_code=404, detail="Монтажник не найден в вашей команде")

    installer = db.query(User).filter(User.id == uid).first()
    if not installer:
        raise HTTPException(status_code=404, detail="Пользователь не найден")

    # Запрос смен с фильтром по дате
    query = db.query(Shift).filter(Shift.user_id == uid)
    if from_date:
        query = query.filter(Shift.start_at >= from_date)
    if to_date:
        query = query.filter(Shift.start_at <= to_date)

    shifts = query.all()

    total_pause = sum(s.pause_seconds or 0 for s in shifts)
    total_idle = sum(s.idle_seconds or 0 for s in shifts)
    total_work = sum(s.total_seconds or 0 for s in shifts)
    count = len(shifts)

    idle_pct = 0.0
    if total_work > 0:
        idle_pct = round((total_pause + total_idle) / total_work * 100, 1)

    return PauseStatsOut(
        installer_id=str(uid),
        installer_name=installer.full_name,
        total_shifts=count,
        total_pause_seconds=total_pause,
        total_idle_seconds=total_idle,
        total_work_seconds=total_work,
        avg_pause_per_shift=round(total_pause / max(count, 1), 0),
        avg_idle_per_shift=round(total_idle / max(count, 1), 0),
        idle_percentage=idle_pct,
    )


# =====================================================
# 3.15 — Quick reassign installer to another site object
# =====================================================

class ReassignRequest(BaseModel):
    site_object_id: str


@router.post("/{installer_id}/reassign")
def reassign_installer(
    installer_id: str,
    payload: ReassignRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Перевести монтажника на другой объект.
    Обновляет site_object_id активной смены.
    """
    if current_user.role not in ("foreman", "FOREMAN"):
        raise HTTPException(status_code=403, detail="Доступ только для бригадира")

    try:
        uid = PyUUID(installer_id)
    except ValueError:
        raise HTTPException(status_code=400, detail="Invalid installer_id")

    # Проверяем что монтажник в команде
    membership = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.installer_user_id == uid,
            ForemanMembership.status == "active"
        )
        .first()
    )
    if not membership:
        raise HTTPException(status_code=404, detail="Монтажник не найден в вашей команде")

    # Находим активную смену
    active_shift = (
        db.query(Shift)
        .filter(Shift.user_id == uid, Shift.finish_at.is_(None))
        .first()
    )
    if not active_shift:
        raise HTTPException(status_code=400, detail="У монтажника нет активной смены")

    # Проверяем существование объекта
    from .models import SiteObject
    site_obj = db.query(SiteObject).filter(
        SiteObject.id == PyUUID(payload.site_object_id)
    ).first()
    if not site_obj:
        raise HTTPException(status_code=404, detail="Объект не найден")

    # Обновляем site_object_id если такое поле есть
    if hasattr(active_shift, "site_object_id"):
        active_shift.site_object_id = PyUUID(payload.site_object_id)
    db.commit()

    return {
        "success": True,
        "shift_id": str(active_shift.id),
        "site_object_id": payload.site_object_id,
        "site_name": site_obj.name,
    }
