from __future__ import annotations

from fastapi import APIRouter, Depends
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session

from .db import get_db
from .auth import get_current_user
from .models import User, UserProfile

router = APIRouter(prefix="/profile", tags=["profile"])


class ProfileOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    user_id: str
    full_name: str | None = None
    city: str | None = None
    email: str | None = None
    telegram: str | None = None
    about: str | None = None


class ProfileUpsert(BaseModel):
    full_name: str | None = None
    city: str | None = None
    email: str | None = None
    telegram: str | None = None
    about: str | None = None


@router.get("/me", response_model=ProfileOut)
def get_my_profile(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    profile = db.query(UserProfile).filter(
        UserProfile.user_id == current_user.id
    ).first()

    if not profile:
        return ProfileOut(user_id=str(current_user.id))

    return ProfileOut(
        user_id=str(profile.user_id),
        full_name=profile.full_name,
        city=profile.city,
        email=profile.email,
        telegram=profile.telegram,
        about=profile.about,
    )


@router.put("/me", response_model=ProfileOut)
def upsert_my_profile(
    payload: ProfileUpsert,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    profile = db.query(UserProfile).filter(
        UserProfile.user_id == current_user.id
    ).first()

    if profile:
        # Update existing
        profile.full_name = payload.full_name
        profile.city = payload.city
        profile.email = payload.email
        profile.telegram = payload.telegram
        profile.about = payload.about
    else:
        # Create new
        profile = UserProfile(
            user_id=current_user.id,
            full_name=payload.full_name,
            city=payload.city,
            email=payload.email,
            telegram=payload.telegram,
            about=payload.about,
        )
        db.add(profile)

    # ВАЖНО: Синхронизируем имя с таблицей users
    # чтобы имя отображалось в сменах и отчётах
    if payload.full_name:
        parts = payload.full_name.strip().split(maxsplit=1)
        current_user.first_name = parts[0] if parts else None
        current_user.last_name = parts[1] if len(parts) > 1 else None

    db.commit()
    db.refresh(profile)

    return ProfileOut(
        user_id=str(profile.user_id),
        full_name=profile.full_name,
        city=profile.city,
        email=profile.email,
        telegram=profile.telegram,
        about=profile.about,
    )
