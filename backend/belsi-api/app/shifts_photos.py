from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from uuid import UUID

from .db import get_db

# ВАЖНО: тут импорт зависит от твоего проекта.
# У тебя уже есть логика токена (Bearer demo-token-...).
# Обычно это что-то вроде get_current_user.
from .auth import get_current_user  # если файла auth.py нет — скажи, подстрою

from .models import Shift, ShiftPhoto
from .schemas_shift_photos import ShiftPhotoOut

router = APIRouter(prefix="/shifts", tags=["shifts"])

@router.get("/{shift_id}/photos", response_model=list[ShiftPhotoOut])
def list_shift_photos(
    shift_id: UUID,
    db: Session = Depends(get_db),
    user = Depends(get_current_user),
):
    shift = db.query(Shift).filter(Shift.id == shift_id).first()
    if not shift:
        raise HTTPException(status_code=404, detail="Shift not found")

    # Шаг 1: пока только владелец смены
    if shift.user_id != user.id:
        raise HTTPException(status_code=403, detail="Forbidden")

    photos = (
        db.query(ShiftPhoto)
        .filter(ShiftPhoto.shift_id == shift_id)
        .order_by(ShiftPhoto.created_at.asc())
        .all()
    )
    return photos
