"""
User management module
Handles user info, names, short IDs, stats, roles, avatars
Provides both /user/ and /users/ routes for compatibility
"""
from __future__ import annotations

import secrets
from typing import Optional
from uuid import UUID
from datetime import datetime

from fastapi import APIRouter, Depends, HTTPException, UploadFile, File
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session
from sqlalchemy import func

from .db import get_db
from .auth import get_current_user
from .models import User, Shift, ShiftPhoto
from .storage import save_shift_photo

router = APIRouter(tags=["user"])


# ============= Schemas =============

class UserNameUpdate(BaseModel):
    first_name: Optional[str] = Field(None, min_length=1, max_length=100)
    last_name: Optional[str] = Field(None, min_length=1, max_length=100)


class UserResponse(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    phone: str
    role: str
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    full_name: str
    short_id: Optional[str] = None
    foreman_id: Optional[UUID] = None
    created_at: datetime
    # Sticky binding к объекту, переживает minimize/kill приложения
    current_site_object_id: Optional[UUID] = None


class UpdateProfileRequest(BaseModel):
    """Request schema for profile update - supports both snake_case and camelCase"""
    full_name: Optional[str] = Field(None, alias="fullName")
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    email: Optional[str] = None
    
    model_config = ConfigDict(populate_by_name=True)


class UpdateUserRequest(BaseModel):
    name: Optional[str] = None
    email: Optional[str] = None


class UpdateRoleRequest(BaseModel):
    role: str


class AvatarUploadResponse(BaseModel):
    avatarUrl: str


class UserStatsResponse(BaseModel):
    totalShifts: int
    totalHours: int
    totalEarned: float
    averageRating: float
    completedPhotos: int
    rejectedPhotos: int


# ============= Helpers =============

def _generate_short_id() -> str:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alphabet) for _ in range(6))


def _ensure_short_id(db: Session, user: User) -> str:
    """Ensure user has a short_id, generate one if missing"""
    if user.short_id:
        return user.short_id
    for _ in range(20):
        candidate = _generate_short_id()
        existing = db.query(User).filter(User.short_id == candidate).first()
        if not existing:
            user.short_id = candidate
            db.commit()
            db.refresh(user)
            return candidate
    return None


def _user_to_response(user: User) -> dict:
    return {
        "id": user.id,
        "phone": user.phone,
        "role": user.role,
        "first_name": user.first_name,
        "last_name": user.last_name,
        "full_name": user.full_name,
        "short_id": user.short_id,
        "created_at": user.created_at,
    }


# ============= /user/ routes (singular - original) =============

@router.get("/user/me", response_model=UserResponse)
def get_current_user_info(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _ensure_short_id(db, current_user)
    return current_user


@router.put("/user/me", response_model=UserResponse)
def update_current_user(
    update_data: UpdateProfileRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Update current user's profile.
    Supports both snake_case and camelCase field names.
    Saves first_name and last_name to users table.
    """
    # Если переданы first_name/last_name напрямую - используем их
    if update_data.first_name is not None:
        current_user.first_name = update_data.first_name.strip()
    if update_data.last_name is not None:
        current_user.last_name = update_data.last_name.strip() if update_data.last_name else None
    
    # Если передано full_name и first_name не был передан - разбиваем full_name
    if update_data.full_name is not None and update_data.first_name is None:
        parts = update_data.full_name.strip().split(" ", 1)
        current_user.first_name = parts[0] if parts else None
        current_user.last_name = parts[1] if len(parts) > 1 else None
    
    # Email можно сохранить в UserProfile если нужно
    # (пока пропускаем, т.к. users таблица не имеет email)
    
    db.commit()
    db.refresh(current_user)
    return current_user


@router.put("/user/me/name", response_model=UserResponse)
def update_user_name(
    update_data: UserNameUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    if update_data.first_name is not None:
        current_user.first_name = update_data.first_name
    if update_data.last_name is not None:
        current_user.last_name = update_data.last_name
    db.commit()
    db.refresh(current_user)
    return current_user




# FIX(2026-05-11) BELSI 2.0.0 security: restored /user/me/role с safe constraints.
# Ранее endpoint позволял ЛЮБОМУ юзеру сменить роль на curator (privilege escalation).
# Сейчас разрешено только:
#   1) Установить роль "installer" (default, не повышение)
#   2) Уточнить роль если текущая NULL/пустая (первичный onboarding после signup)
# Все остальные смены роли — через curator-only POST /users/{user_id}/role.
@router.post("/user/me/role", response_model=UserResponse)
def update_current_user_role(
    request: UpdateRoleRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Self-set role (только для первичного onboarding / возврат к installer)."""
    SAFE_SELF_ROLES = {"installer"}
    new_role = (request.role or "").strip().lower()
    if new_role not in SAFE_SELF_ROLES:
        raise HTTPException(
            status_code=403,
            detail="Самостоятельно можно установить только роль 'installer'. "
                   "Для других ролей обратитесь к куратору."
        )
    current_user.role = new_role
    db.commit()
    db.refresh(current_user)
    return current_user

# ============= /users/ routes (plural - app compatibility) =============

@router.get("/users/{user_id}", response_model=UserResponse)
def get_user_by_id(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user


@router.get("/user/{user_id}", response_model=UserResponse)
def get_user_by_id_singular(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    return get_user_by_id(user_id, db, current_user)


@router.put("/users/{user_id}", response_model=UserResponse)
def update_user(
    user_id: UUID,
    request: UpdateUserRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    # Only curator or user themselves can update
    role = (current_user.role or "").lower()
    if role != "curator" and current_user.id != user_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    if request.name is not None:
        parts = request.name.strip().split(" ", 1)
        user.first_name = parts[0] if parts else None
        user.last_name = parts[1] if len(parts) > 1 else None
    db.commit()
    db.refresh(user)
    return user


@router.post("/users/{user_id}/role", response_model=UserResponse)
def update_user_role(
    user_id: UUID,
    request: UpdateRoleRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    # Only curators can change roles
    role = (current_user.role or "").lower()
    if role != "curator":
        raise HTTPException(status_code=403, detail="Curator access required")
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    allowed_roles = ["installer", "foreman", "coordinator", "curator"]
    if request.role not in allowed_roles:
        raise HTTPException(status_code=400, detail=f"Invalid role. Allowed: {allowed_roles}")
    user.role = request.role
    db.commit()
    db.refresh(user)
    return user


@router.post("/users/{user_id}/avatar", response_model=AvatarUploadResponse)
async def upload_avatar(
    user_id: UUID,
    avatar: UploadFile = File(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    role = (current_user.role or "").lower()
    if role != "curator" and current_user.id != user_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    content = await avatar.read()
    if not content:
        raise HTTPException(status_code=400, detail="Empty file")
    try:
        photo_url = await save_shift_photo(content, avatar.filename)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to upload: {e}")
    # FIX(2026-05-11) BELSI 2.0.0: РАНЬШЕ URL возвращался клиенту, но НЕ
    # сохранялся в БД — после перезапуска приложения юзер видел старый avatar.
    # Теперь сохраняем в users.avatar_url.
    user.avatar_url = photo_url
    db.commit()
    return AvatarUploadResponse(avatarUrl=photo_url)


@router.delete("/users/{user_id}/avatar")
def delete_avatar(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    role = (current_user.role or "").lower()
    if role != "curator" and current_user.id != user_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    # FIX(2026-05-11) BELSI 2.0.0: РАНЬШЕ DELETE возвращал success, но фактически
    # ничего не удалял — avatar_url оставался в БД. Теперь очищаем поле.
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    user.avatar_url = None
    db.commit()
    return {"success": True, "message": "Avatar deleted"}


@router.get("/users/{user_id}/stats", response_model=UserStatsResponse)
def get_user_stats(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    user = db.query(User).filter(User.id == user_id).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    total_shifts = db.query(func.count(Shift.id)).filter(Shift.user_id == user_id).scalar() or 0
    total_hours_raw = db.query(func.sum(Shift.duration_hours)).filter(Shift.user_id == user_id).scalar() or 0
    total_hours = int(float(total_hours_raw))

    hourly_rate = (
        db.query(Shift.hourly_rate)
        .filter(Shift.user_id == user_id, Shift.hourly_rate.isnot(None))
        .order_by(Shift.start_at.desc())
        .first()
    )
    rate = float(hourly_rate[0]) if hourly_rate and hourly_rate[0] else 0.0
    total_earned = round(float(total_hours_raw) * rate, 2)

    completed_photos = (
        db.query(func.count(ShiftPhoto.id))
        .join(Shift)
        .filter(Shift.user_id == user_id, ShiftPhoto.status == "approved")
        .scalar() or 0
    )
    rejected_photos = (
        db.query(func.count(ShiftPhoto.id))
        .join(Shift)
        .filter(Shift.user_id == user_id, ShiftPhoto.status == "rejected")
        .scalar() or 0
    )

    return UserStatsResponse(
        totalShifts=total_shifts,
        totalHours=total_hours,
        totalEarned=total_earned,
        averageRating=0.0,
        completedPhotos=completed_photos,
        rejectedPhotos=rejected_photos,
    )


# ============= Short ID Lookup =============

@router.get("/users/lookup/{short_id}")
def lookup_user_by_short_id(
    short_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Look up a user by their short 6-character ID"""
    user = db.query(User).filter(User.short_id == short_id.upper()).first()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return _user_to_response(user)


# ===== Sticky current site object =====
class CurrentObjectRequest(BaseModel):
    site_object_id: Optional[UUID] = None


@router.post("/user/me/current-object", response_model=UserResponse)
def set_current_site_object(
    payload: CurrentObjectRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Записать sticky-binding пользователь<->объект.
    NULL = открепиться от объекта.
    Без UNIQUE — несколько монтажников могут быть на одном объекте.
    """
    current_user.current_site_object_id = payload.site_object_id
    db.commit()
    db.refresh(current_user)
    return current_user
