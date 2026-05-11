"""
Photo management endpoints
Handles photo CRUD, review, and listing by shift
"""
from __future__ import annotations

from typing import Optional, List
from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Body
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import ShiftPhoto, Shift, User

router = APIRouter(prefix="/photos", tags=["photo_review"])


class PhotoReviewRequest(BaseModel):
    status: str = Field(..., pattern="^(approved|rejected)$")
    comment: Optional[str] = Field(None, max_length=500)


class PhotoOut(BaseModel):
    id: UUID
    shift_id: UUID
    hour_label: Optional[str]
    status: str
    comment: Optional[str]
    photo_url: str
    created_at: datetime
    ai_comment: Optional[str] = None

    class Config:
        from_attributes = True


class UpdatePhotoRequest(BaseModel):
    comment: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None


def _role(u) -> str:
    r = getattr(u, "role", "") or ""
    return r.strip().lower()


# ============= GET /photos/pending =============

@router.get("/pending", response_model=List[PhotoOut])
def get_pending_photos(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get all photos with pending status for review"""
    role = _role(current_user)

    query = db.query(ShiftPhoto).filter(ShiftPhoto.status == "pending")

    if role == "foreman":
        # Foreman sees only their team photos
        team_sql = text("""
            SELECT installer_user_id FROM foreman_memberships
            WHERE foreman_user_id = :foreman_id
        """)
        team_ids = [row[0] for row in db.execute(team_sql, {"foreman_id": str(current_user.id)}).fetchall()]
        if team_ids:
            query = query.join(Shift).filter(Shift.user_id.in_(team_ids))
        else:
            return []
    elif role == "installer":
        # Installer sees only their own photos
        query = query.join(Shift).filter(Shift.user_id == current_user.id)
    # curator sees all

    photos = query.order_by(ShiftPhoto.created_at.desc()).all()
    return photos


# ============= GET /photos/shift/{shift_id} =============

@router.get("/shift/{shift_id}", response_model=List[PhotoOut])
def get_photos_by_shift(
    shift_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get all photos for a specific shift"""
    shift = db.query(Shift).filter(Shift.id == shift_id).first()
    if not shift:
        raise HTTPException(status_code=404, detail="Shift not found")

    photos = (
        db.query(ShiftPhoto)
        .filter(ShiftPhoto.shift_id == shift_id)
        .order_by(ShiftPhoto.created_at.asc())
        .all()
    )
    return photos


# ============= GET /photos/{photo_id} =============

@router.get("/{photo_id}", response_model=PhotoOut)
def get_photo(
    photo_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get a single photo by ID"""
    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    return photo


# ============= PUT /photos/{photo_id} =============

@router.put("/{photo_id}", response_model=PhotoOut)
def update_photo(
    photo_id: UUID,
    request: UpdatePhotoRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update photo metadata (comment, location)"""
    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    # Check ownership
    shift = db.query(Shift).filter(Shift.id == photo.shift_id).first()
    role = _role(current_user)
    if role not in ["curator", "foreman"] and (not shift or shift.user_id != current_user.id):
        raise HTTPException(status_code=403, detail="Forbidden")

    if request.comment is not None:
        photo.comment = request.comment

    db.commit()
    db.refresh(photo)
    return photo


# ============= DELETE /photos/{photo_id} =============

@router.delete("/{photo_id}")
def delete_photo(
    photo_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Delete a photo"""
    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    # Check ownership
    shift = db.query(Shift).filter(Shift.id == photo.shift_id).first()
    role = _role(current_user)
    if role != "curator" and (not shift or shift.user_id != current_user.id):
        raise HTTPException(status_code=403, detail="Forbidden")

    db.delete(photo)
    db.commit()
    return {"success": True}


# ============= POST /photos/{photo_id}/review =============

@router.post("/{photo_id}/review", response_model=PhotoOut)
def review_photo(
    photo_id: UUID,
    review: PhotoReviewRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Review a photo (approve/reject)"""
    role = _role(current_user)
    if role not in ["curator", "foreman"]:
        raise HTTPException(status_code=403, detail="Only curator and foreman can review photos")

    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")

    photo.status = review.status

    if review.comment:
        reviewer_name = getattr(current_user, "full_name", None) or current_user.phone
        new_comment = f"[{role.upper()}] {reviewer_name}: {review.comment}"
        photo.comment = f"{photo.comment}\n{new_comment}" if photo.comment else new_comment

    db.commit()
    db.refresh(photo)
    return photo


# ============= POST /photos/{photo_id}/approve =============

@router.post("/{photo_id}/approve", response_model=PhotoOut)
def approve_photo(
    photo_id: UUID,
    comment: Optional[str] = Body(None, embed=True, max_length=500),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Approve a photo (shortcut)"""
    return review_photo(
        photo_id=photo_id,
        review=PhotoReviewRequest(status="approved", comment=comment),
        db=db,
        current_user=current_user,
    )


# ============= POST /photos/{photo_id}/reject =============

@router.post("/{photo_id}/reject", response_model=PhotoOut)
def reject_photo(
    photo_id: UUID,
    comment: Optional[str] = Body(None, embed=True, max_length=500),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Reject a photo (shortcut)"""
    return review_photo(
        photo_id=photo_id,
        review=PhotoReviewRequest(status="rejected", comment=comment),
        db=db,
        current_user=current_user,
    )
