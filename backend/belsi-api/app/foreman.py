"""
Полный функционал для бригадира
Версия: 2.0 - с управлением командой, инструментами и проверкой фото
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Optional, List
from uuid import UUID as PyUUID

import secrets
import string

from fastapi import APIRouter, Depends, HTTPException, status, Body
from pydantic import BaseModel, Field, ConfigDict
from sqlalchemy.orm import Session
from sqlalchemy import func, and_, or_

from .db import get_db
from .auth import get_current_user
from .models import (
    User, ForemanInvite, ForemanMembership,
    ShiftPhoto, Shift, Task, Tool, ToolTransaction
)

router = APIRouter(prefix="/foreman", tags=["foreman"])

INVITE_TTL_DAYS = 7  # Срок действия инвайт-кода


# ============= Schemas =============

class InviteOut(BaseModel):
    """Инвайт-код бригадира"""
    model_config = ConfigDict(from_attributes=True)

    id: PyUUID
    code: str
    foreman_phone: str
    installer_phone: Optional[str] = None
    status: str  # new, accepted, cancelled, expired
    created_at: datetime
    expires_at: Optional[datetime] = None
    used_at: Optional[datetime] = None


class InviteCreateResponse(BaseModel):
    """Ответ при создании инвайта"""
    id: PyUUID
    code: str
    expires_at: datetime
    share_text: str  # Текст для отправки монтажнику


class InviteRedeemRequest(BaseModel):
    """Запрос на активацию инвайт-кода"""
    code: str = Field(..., min_length=4, max_length=16)


class TeamMemberOut(BaseModel):
    """Монтажник в команде бригадира"""
    id: PyUUID
    phone: str
    full_name: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None

    # Статус работы
    last_shift_at: Optional[datetime] = None
    active_shift_id: Optional[PyUUID] = None
    is_working_now: bool = False

    # Фото
    last_photo_at: Optional[datetime] = None
    pending_photos_count: int = 0

    # Статистика
    total_shifts: int = 0
    total_hours: float = 0.0

    # Когда присоединился к команде
    joined_at: datetime


class PhotoForReviewOut(BaseModel):
    """Фото монтажника для проверки бригадиром"""
    id: PyUUID
    installer_id: PyUUID
    installer_name: str
    shift_id: PyUUID
    photo_url: str
    hour_label: Optional[str] = None
    status: str  # pending, approved, rejected
    comment: Optional[str] = None
    category: str = "hourly"  # hourly / problem / question
    created_at: datetime
    ai_comment: Optional[str] = None
    ai_score: Optional[int] = None
    ai_category: Optional[str] = None


class PhotosListResponse(BaseModel):
    """Ответ со списком фотографий для Android"""
    photos: List[PhotoForReviewOut]


class PhotoReviewRequest(BaseModel):
    """Проверка фото бригадиром"""
    status: str = Field(..., pattern="^(approved|rejected)$")
    comment: Optional[str] = Field(None, max_length=500)


class TaskCreateRequest(BaseModel):
    """Создание задачи для монтажника"""
    installer_id: PyUUID
    title: str = Field(..., min_length=1, max_length=200)
    description: str = Field(..., min_length=1, max_length=1000)
    priority: str = Field("medium", pattern="^(low|medium|high)$")
    deadline: Optional[datetime] = None


class PhotoReminderRequest(BaseModel):
    """Напоминание монтажнику о фото"""
    installer_id: PyUUID
    message: Optional[str] = Field(None, max_length=500)


class ToolIssueRequest(BaseModel):
    """Выдача инструмента"""
    tool_id: PyUUID
    installer_id: PyUUID
    comment: Optional[str] = None


class ToolReturnRequest(BaseModel):
    """Приём инструмента обратно"""
    transaction_id: PyUUID
    condition: str = Field(..., pattern="^(good|damaged|broken)$")
    comment: Optional[str] = None


class ToolOut(BaseModel):
    """Инструмент бригадира"""
    model_config = ConfigDict(from_attributes=True)

    id: PyUUID
    name: str
    description: Optional[str] = None
    serial_number: Optional[str] = None
    photo_url: Optional[str] = None
    status: str  # available, issued, lost, repair

    # Если выдан - информация о монтажнике
    issued_to_id: Optional[PyUUID] = None
    issued_to_name: Optional[str] = None
    issued_at: Optional[datetime] = None


class ToolTransactionOut(BaseModel):
    """История выдачи/возврата инструмента"""
    model_config = ConfigDict(from_attributes=True)

    id: PyUUID
    tool_id: PyUUID
    tool_name: str
    installer_id: PyUUID
    installer_name: str

    issued_at: datetime
    issue_comment: Optional[str] = None

    returned_at: Optional[datetime] = None
    return_condition: Optional[str] = None
    return_comment: Optional[str] = None

    status: str  # issued, returned


# ============= Helper Functions =============

def _now_utc() -> datetime:
    """Текущее время UTC"""
    return datetime.now(timezone.utc)


def _gen_code(length: int = 6) -> str:
    """Генерация инвайт-кода"""
    alphabet = string.ascii_uppercase + string.digits
    return "".join(secrets.choice(alphabet) for _ in range(length))


def _require_foreman(user: User):
    """Проверка что пользователь - бригадир"""
    if (user.role or "").lower() != "foreman":
        raise HTTPException(status_code=403, detail="Foreman access required")


def _is_my_installer(db: Session, foreman_id: PyUUID, installer_id: PyUUID) -> bool:
    """Проверка что монтажник в команде бригадира"""
    membership = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == foreman_id,
            ForemanMembership.installer_user_id == installer_id,
            ForemanMembership.status == "active"
        )
        .first()
    )
    return membership is not None


# ============= Invite Codes Endpoints =============

@router.get("/invites")
def list_my_invites(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Получить все инвайт-коды бригадира

    Возвращает список всех созданных кодов со статусами:
    - new: ещё не использован
    - accepted: активирован монтажником
    - cancelled: отменён бригадиром
    - expired: истёк срок действия
    """
    _require_foreman(current_user)

    invites = (
        db.query(ForemanInvite)
        .filter(ForemanInvite.foreman_phone == current_user.phone)
        .order_by(ForemanInvite.created_at.desc())
        .all()
    )

    # Проверяем истёкшие
    now = _now_utc()
    result = []

    for invite in invites:
        # Автоматически помечаем истёкшие
        if (invite.status == "new" and
            invite.expires_at and
            invite.expires_at <= now):
            invite.status = "expired"
            db.commit()

        result.append(InviteOut(
            id=invite.id,
            code=invite.code,
            foreman_phone=invite.foreman_phone,
            installer_phone=invite.installer_phone,
            status=invite.status,
            created_at=invite.created_at,
            expires_at=invite.expires_at,
            used_at=invite.used_at
        ))

    return {"items": result}


@router.post("/invites", response_model=InviteOut)
def create_invite(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Создать новый инвайт-код для монтажника

    Бригадир создаёт код, который может отправить монтажнику.
    Монтажник активирует код через /foreman/invites/redeem
    """
    _require_foreman(current_user)

    # Генерируем уникальный код
    max_attempts = 10
    code = None

    for _ in range(max_attempts):
        code = _gen_code(6)
        existing = (
            db.query(ForemanInvite)
            .filter(ForemanInvite.code == code)
            .first()
        )
        if not existing:
            break

    if not code:
        raise HTTPException(
            status_code=500,
            detail="Failed to generate unique code"
        )

    # Создаём инвайт
    expires_at = _now_utc() + timedelta(days=INVITE_TTL_DAYS)

    new_invite = ForemanInvite(
        code=code,
        foreman_phone=current_user.phone,
        foreman_user_id=current_user.id,
        status="new",
        expires_at=expires_at
    )

    db.add(new_invite)
    db.commit()
    db.refresh(new_invite)

    return InviteOut(
        id=new_invite.id,
        code=code,
        foreman_phone=current_user.phone,
        installer_phone=None,
        status="new",
        created_at=new_invite.created_at,
        expires_at=expires_at,
        used_at=None
    )


@router.post("/invites/redeem")
def redeem_invite(
    request: InviteRedeemRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Активировать инвайт-код (монтажник присоединяется к бригадиру)

    Монтажник вводит код, который дал ему бригадир.
    После активации монтажник привязывается к бригадиру.
    """
    # Проверяем что это монтажник
    if (current_user.role or "").lower() != "installer":
        raise HTTPException(
            status_code=403,
            detail="Only installers can redeem invite codes"
        )

    # Ищем инвайт
    invite = (
        db.query(ForemanInvite)
        .filter(ForemanInvite.code == request.code.upper())
        .first()
    )

    if not invite:
        raise HTTPException(
            status_code=404,
            detail="Invite code not found"
        )

    # Проверки
    now = _now_utc()

    if invite.status == "accepted":
        raise HTTPException(
            status_code=400,
            detail="Invite code already used"
        )

    if invite.status == "cancelled":
        raise HTTPException(
            status_code=400,
            detail="Invite code was cancelled"
        )

    if invite.expires_at and invite.expires_at <= now:
        invite.status = "expired"
        db.commit()
        raise HTTPException(
            status_code=400,
            detail="Invite code expired"
        )

    # Получаем бригадира
    foreman = (
        db.query(User)
        .filter(User.phone == invite.foreman_phone)
        .first()
    )

    if not foreman:
        raise HTTPException(
            status_code=404,
            detail="Foreman not found"
        )

    # Проверяем что монтажник ещё не в команде этого бригадира
    existing_membership = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == foreman.id,
            ForemanMembership.installer_user_id == current_user.id,
            ForemanMembership.status == "active"
        )
        .first()
    )

    if existing_membership:
        raise HTTPException(
            status_code=400,
            detail="You are already in this foreman's team"
        )

    # Активируем инвайт
    invite.status = "accepted"
    invite.installer_phone = current_user.phone
    invite.used_at = now

    # Создаём связь в foreman_memberships
    membership = ForemanMembership(
        foreman_user_id=foreman.id,
        installer_user_id=current_user.id,
        status="active"
    )

    db.add(membership)
    db.commit()

    foreman_name = getattr(foreman, 'full_name', None) or foreman.phone

    return {
        "success": True,
        "foreman_name": foreman_name,
        "message": f"Вы успешно присоединились к команде бригадира {foreman_name}"
    }


class InviteCancelRequest(BaseModel):
    """Запрос на отмену инвайт-кода"""
    code: str = Field(..., min_length=4, max_length=16)


@router.post("/invites/cancel")
def cancel_invite(
    request: InviteCancelRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Отменить инвайт-код

    Бригадир может отменить неиспользованный код.
    """
    _require_foreman(current_user)

    invite = (
        db.query(ForemanInvite)
        .filter(
            ForemanInvite.code == request.code.upper(),
            ForemanInvite.foreman_phone == current_user.phone
        )
        .first()
    )

    if not invite:
        raise HTTPException(
            status_code=404,
            detail="Invite not found"
        )

    if invite.status == "accepted":
        raise HTTPException(
            status_code=400,
            detail="Cannot cancel already accepted invite"
        )

    invite.status = "cancelled"
    db.commit()

    return InviteOut(
        id=invite.id,
        code=invite.code,
        foreman_phone=invite.foreman_phone,
        installer_phone=invite.installer_phone,
        status=invite.status,
        created_at=invite.created_at,
        expires_at=invite.expires_at,
        used_at=invite.used_at
    )


# ============= Team Management =============

@router.get("/team", response_model=List[TeamMemberOut])
def get_my_team(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Получить список монтажников в команде

    Бригадир видит:
    - Имя, фамилию, фото монтажника
    - Статус работы (работает сейчас или нет)
    - Последнее фото
    - Количество фото на проверке
    - Статистику смен
    """
    _require_foreman(current_user)

    # Получаем всех монтажников
    memberships = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.status == "active"
        )
        .all()
    )

    result = []
    now = _now_utc()

    for membership in memberships:
        installer = (
            db.query(User)
            .filter(User.id == membership.installer_user_id)
            .first()
        )

        if not installer:
            continue

        # Последняя смена
        last_shift = (
            db.query(Shift)
            .filter(Shift.user_id == installer.id)
            .order_by(Shift.start_at.desc())
            .first()
        )

        # Активная смена (если есть)
        active_shift = (
            db.query(Shift)
            .filter(
                Shift.user_id == installer.id,
                Shift.finish_at.is_(None)
            )
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
            .filter(
                Shift.user_id == installer.id,
                ShiftPhoto.status == "pending"
            )
            .scalar() or 0
        )

        # Статистика
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

        result.append(TeamMemberOut(
            id=installer.id,
            phone=installer.phone,
            full_name=getattr(installer, 'full_name', None),
            first_name=getattr(installer, 'first_name', None),
            last_name=getattr(installer, 'last_name', None),
            last_shift_at=last_shift.start_at if last_shift else None,
            active_shift_id=active_shift.id if active_shift else None,
            is_working_now=active_shift is not None,
            last_photo_at=last_photo.created_at if last_photo else None,
            pending_photos_count=pending_photos,
            total_shifts=total_shifts,
            total_hours=float(total_hours),
            joined_at=membership.created_at
        ))

    return result


@router.delete("/team/{installer_id}")
def remove_from_team(
    installer_id: PyUUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Удалить монтажника из команды

    Бригадир может убрать монтажника из своей команды.
    """
    _require_foreman(current_user)

    membership = (
        db.query(ForemanMembership)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.installer_user_id == installer_id,
            ForemanMembership.status == "active"
        )
        .first()
    )

    if not membership:
        raise HTTPException(
            status_code=404,
            detail="Installer not in your team"
        )

    membership.status = "inactive"
    db.commit()

    return InviteOut(
        id=invite.id,
        code=invite.code,
        foreman_phone=invite.foreman_phone,
        installer_phone=invite.installer_phone,
        status=invite.status,
        created_at=invite.created_at,
        expires_at=invite.expires_at,
        used_at=invite.used_at
    )


# ============= Photo Review =============

@router.get("/photos", response_model=PhotosListResponse)
def get_photos_for_review(
    installer_id: Optional[PyUUID] = None,
    status: str = "pending",
    limit: int = 50,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Получить фото монтажников для проверки

    Бригадир видит фото своих монтажников.
    Может фильтровать по статусу и конкретному монтажнику.
    """
    _require_foreman(current_user)

    # Получаем ID монтажников в команде
    team_ids_query = (
        db.query(ForemanMembership.installer_user_id)
        .filter(
            ForemanMembership.foreman_user_id == current_user.id,
            ForemanMembership.status == "active"
        )
    )

    team_ids = [row[0] for row in team_ids_query.all()]

    if not team_ids:
        return PhotosListResponse(photos=[])

    # Запрос фото
    query = (
        db.query(ShiftPhoto)
        .join(Shift)
        .filter(Shift.user_id.in_(team_ids))
    )

    # Фильтры
    if installer_id:
        if not _is_my_installer(db, current_user.id, installer_id):
            raise HTTPException(
                status_code=403,
                detail="This installer is not in your team"
            )
        query = query.filter(Shift.user_id == installer_id)

    if status:
        query = query.filter(ShiftPhoto.status == status)

    photos = query.order_by(ShiftPhoto.created_at.desc()).limit(limit).all()

    result = []
    for photo in photos:
        shift = db.query(Shift).filter(Shift.id == photo.shift_id).first()
        if not shift:
            continue

        installer = db.query(User).filter(User.id == shift.user_id).first()
        if not installer:
            continue

        installer_name = getattr(installer, 'full_name', None) or installer.phone

        result.append(PhotoForReviewOut(
            id=photo.id,
            installer_id=installer.id,
            installer_name=installer_name,
            shift_id=shift.id,
            photo_url=photo.photo_url,
            hour_label=photo.hour_label,
            status=photo.status,
            comment=photo.comment,
            category=getattr(photo, "category", "hourly"),
            created_at=photo.created_at,
            ai_comment=getattr(photo, "ai_comment", None),
            ai_score=getattr(photo, "ai_score", None),
            ai_category=getattr(photo, "ai_category", "unknown")
        ))

    return PhotosListResponse(photos=result)


@router.post("/photos/{photo_id}/review")
def review_photo(
    photo_id: PyUUID,
    review: PhotoReviewRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Проверить фото монтажника (одобрить или отклонить)

    Бригадир может проверять фото своих монтажников.
    """
    _require_foreman(current_user)

    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    # Проверяем что это фото нашего монтажника
    shift = db.query(Shift).filter(Shift.id == photo.shift_id).first()
    if not shift:
        raise HTTPException(status_code=404, detail="Shift not found")

    if not _is_my_installer(db, current_user.id, shift.user_id):
        raise HTTPException(
            status_code=403,
            detail="You can only review photos of your team members"
        )

    # Обновляем статус
    photo.status = review.status

    # Добавляем комментарий
    if review.comment:
        foreman_name = getattr(current_user, 'full_name', None) or current_user.phone
        new_comment = f"[FOREMAN] {foreman_name}: {review.comment}"

        if photo.comment:
            photo.comment = f"{photo.comment}\n{new_comment}"
        else:
            photo.comment = new_comment

    db.commit()

    return InviteOut(
        id=invite.id,
        code=invite.code,
        foreman_phone=invite.foreman_phone,
        installer_phone=invite.installer_phone,
        status=invite.status,
        created_at=invite.created_at,
        expires_at=invite.expires_at,
        used_at=invite.used_at
    )


# ============= Task Management =============

@router.post("/tasks")
def create_task_for_installer(
    task_request: TaskCreateRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Создать задачу для монтажника

    Бригадир может ставить задачи своим монтажникам.
    """
    _require_foreman(current_user)

    # Проверяем что монтажник в нашей команде
    if not _is_my_installer(db, current_user.id, task_request.installer_id):
        raise HTTPException(
            status_code=403,
            detail="This installer is not in your team"
        )

    # Создаём задачу
    new_task = Task(
        created_by=current_user.id,
        assigned_to=task_request.installer_id,
        title=task_request.title,
        description=task_request.description,
        priority=task_request.priority,
        deadline=task_request.deadline,
        status="pending"
    )

    db.add(new_task)
    db.commit()
    db.refresh(new_task)

    return {
        "success": True,
        "task_id": new_task.id
    }


@router.post("/reminders/photo")
def send_photo_reminder(
    reminder: PhotoReminderRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Напомнить монтажнику о фото

    Бригадир может напомнить монтажнику сделать фото.
    Создаётся задача с напоминанием.
    """
    _require_foreman(current_user)

    # Проверяем что монтажник в команде
    if not _is_my_installer(db, current_user.id, reminder.installer_id):
        raise HTTPException(
            status_code=403,
            detail="This installer is not in your team"
        )

    # Создаём задачу-напоминание
    message = reminder.message or "Не забудьте сделать фото работы!"

    new_task = Task(
        created_by=current_user.id,
        assigned_to=reminder.installer_id,
        title="Напоминание: Сделайте фото",
        description=message,
        priority="high",
        status="pending"
    )

    db.add(new_task)
    db.commit()

    return {
        "success": True,
        "message": "Reminder sent"
    }


# ============= Tools Management =============

@router.get("/tools", response_model=List[ToolOut])
def get_my_tools(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Получить список инструментов бригадира

    Показывает все инструменты со статусами:
    - available: доступен
    - issued: выдан монтажнику
    - lost: утерян
    - repair: в ремонте
    """
    _require_foreman(current_user)

    tools = (
        db.query(Tool)
        .filter(Tool.foreman_id == current_user.id)
        .order_by(Tool.name)
        .all()
    )

    result = []
    for tool in tools:
        # Если инструмент выдан - находим кому
        issued_to_id = None
        issued_to_name = None
        issued_at = None

        if tool.status == "issued":
            transaction = (
                db.query(ToolTransaction)
                .filter(
                    ToolTransaction.tool_id == tool.id,
                    ToolTransaction.status == "issued"
                )
                .order_by(ToolTransaction.issued_at.desc())
                .first()
            )

            if transaction:
                installer = (
                    db.query(User)
                    .filter(User.id == transaction.installer_id)
                    .first()
                )
                if installer:
                    issued_to_id = installer.id
                    issued_to_name = getattr(installer, 'full_name', None) or installer.phone
                    issued_at = transaction.issued_at

        result.append(ToolOut(
            id=tool.id,
            name=tool.name,
            description=tool.description,
            serial_number=tool.serial_number,
            photo_url=tool.photo_url,
            status=tool.status,
            issued_to_id=issued_to_id,
            issued_to_name=issued_to_name,
            issued_at=issued_at
        ))

    return result


@router.post("/tools/issue")
def issue_tool(
    request: ToolIssueRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Выдать инструмент монтажнику

    Бригадир выдаёт свой инструмент монтажнику из команды.
    """
    _require_foreman(current_user)

    # Проверяем что монтажник в команде
    if not _is_my_installer(db, current_user.id, request.installer_id):
        raise HTTPException(
            status_code=403,
            detail="This installer is not in your team"
        )

    # Проверяем инструмент
    tool = (
        db.query(Tool)
        .filter(
            Tool.id == request.tool_id,
            Tool.foreman_id == current_user.id
        )
        .first()
    )

    if not tool:
        raise HTTPException(
            status_code=404,
            detail="Tool not found or doesn't belong to you"
        )

    if tool.status != "available":
        raise HTTPException(
            status_code=400,
            detail=f"Tool is not available (status: {tool.status})"
        )

    # Создаём транзакцию
    transaction = ToolTransaction(
        tool_id=tool.id,
        installer_id=request.installer_id,
        issued_by=current_user.id,
        issue_comment=request.comment,
        status="issued"
    )

    # Обновляем статус инструмента
    tool.status = "issued"

    db.add(transaction)
    db.commit()

    return {
        "success": True,
        "transaction_id": transaction.id
    }


@router.post("/tools/return")
def return_tool(
    request: ToolReturnRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Принять инструмент обратно от монтажника

    Бригадир принимает инструмент и указывает его состояние.
    """
    _require_foreman(current_user)

    # Находим транзакцию
    transaction = (
        db.query(ToolTransaction)
        .filter(
            ToolTransaction.id == request.transaction_id,
            ToolTransaction.status == "issued"
        )
        .first()
    )

    if not transaction:
        raise HTTPException(
            status_code=404,
            detail="Transaction not found or already completed"
        )

    # Проверяем что это наш инструмент
    tool = (
        db.query(Tool)
        .filter(
            Tool.id == transaction.tool_id,
            Tool.foreman_id == current_user.id
        )
        .first()
    )

    if not tool:
        raise HTTPException(
            status_code=403,
            detail="This tool doesn't belong to you"
        )

    # Обновляем транзакцию
    transaction.returned_at = _now_utc()
    transaction.returned_to = current_user.id
    transaction.return_condition = request.condition
    transaction.return_comment = request.comment
    transaction.status = "returned"

    # Обновляем статус инструмента
    if request.condition == "good":
        tool.status = "available"
    elif request.condition == "damaged":
        tool.status = "repair"
    elif request.condition == "broken":
        tool.status = "repair"

    db.commit()

    return {
        "success": True,
        "tool_status": tool.status
    }


@router.get("/tools/history", response_model=List[ToolTransactionOut])
def get_tools_history(
    tool_id: Optional[PyUUID] = None,
    installer_id: Optional[PyUUID] = None,
    limit: int = 50,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    История выдачи/возврата инструментов

    Бригадир видит всю историю операций с инструментами.
    """
    _require_foreman(current_user)

    # Получаем ID наших инструментов
    my_tool_ids = (
        db.query(Tool.id)
        .filter(Tool.foreman_id == current_user.id)
        .all()
    )
    my_tool_ids = [row[0] for row in my_tool_ids]

    if not my_tool_ids:
        return []

    query = (
        db.query(ToolTransaction)
        .filter(ToolTransaction.tool_id.in_(my_tool_ids))
    )

    # Фильтры
    if tool_id:
        query = query.filter(ToolTransaction.tool_id == tool_id)

    if installer_id:
        query = query.filter(ToolTransaction.installer_id == installer_id)

    transactions = query.order_by(ToolTransaction.issued_at.desc()).limit(limit).all()

    result = []
    for trans in transactions:
        tool = db.query(Tool).filter(Tool.id == trans.tool_id).first()
        installer = db.query(User).filter(User.id == trans.installer_id).first()

        if not tool or not installer:
            continue

        installer_name = getattr(installer, 'full_name', None) or installer.phone

        result.append(ToolTransactionOut(
            id=trans.id,
            tool_id=tool.id,
            tool_name=tool.name,
            installer_id=installer.id,
            installer_name=installer_name,
            issued_at=trans.issued_at,
            issue_comment=trans.issue_comment,
            returned_at=trans.returned_at,
            return_condition=trans.return_condition,
            return_comment=trans.return_comment,
            status=trans.status
        ))

    return result
