"""
CRUD для строительных объектов (Site Objects)
Глобальные объекты — видны всем, создаются foreman/coordinator/curator.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, Body, UploadFile, File
from pydantic import BaseModel, Field
from datetime import datetime, timezone
from typing import Optional, List
from uuid import UUID

from sqlalchemy.orm import Session
from sqlalchemy import text, func

from .db import get_db
from .auth import get_current_user
from .models import User, Shift, ShiftPhoto, SiteObject, ShiftSegment
from .storage import save_shift_photo

router = APIRouter(tags=["objects"])


# ============= Schemas =============

class CreateObjectRequest(BaseModel):
    name: str = Field(..., min_length=1, max_length=300)
    address: Optional[str] = None
    description: Optional[str] = None
    coordinator_id: Optional[UUID] = None


class UpdateObjectRequest(BaseModel):
    name: Optional[str] = None
    address: Optional[str] = None
    description: Optional[str] = None
    measurements: Optional[dict] = None
    comments: Optional[str] = None
    status: Optional[str] = None
    photo_urls: Optional[list] = None
    file_urls: Optional[list] = None
    coordinator_id: Optional[UUID] = None


class ChangeObjectRequest(BaseModel):
    site_object_id: UUID


# ============= Helper =============

def _object_to_dict(obj: SiteObject, db: Session) -> dict:
    """Convert SiteObject to response dict with stats"""
    now = datetime.now(timezone.utc)
    today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)

    # Active workers (users with active shift on this object)
    active_workers_count = (
        db.query(func.count(func.distinct(Shift.user_id)))
        .filter(Shift.site_object_id == obj.id, Shift.status == "active")
        .scalar() or 0
    )

    # Shifts today
    shifts_today = (
        db.query(func.count(Shift.id))
        .filter(Shift.site_object_id == obj.id, Shift.start_at >= today_start)
        .scalar() or 0
    )

    # Total photos
    total_photos = (
        db.query(func.count(ShiftPhoto.id))
        .filter(ShiftPhoto.site_object_id == obj.id)
        .scalar() or 0
    )

    # Coordinator name
    coordinator_name = None
    if obj.coordinator_id:
        coord = db.query(User).filter(User.id == obj.coordinator_id).first()
        if coord:
            coordinator_name = coord.full_name

    # Creator name
    creator_name = None
    if obj.created_by:
        creator = db.query(User).filter(User.id == obj.created_by).first()
        if creator:
            creator_name = creator.full_name

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
        "created_by": str(obj.created_by) if obj.created_by else None,
        "creator_name": creator_name,
        "coordinator_id": str(obj.coordinator_id) if obj.coordinator_id else None,
        "coordinator_name": coordinator_name,
        "active_workers_count": active_workers_count,
        "shifts_today": shifts_today,
        "total_photos": total_photos,
        "created_at": obj.created_at.isoformat() if obj.created_at else None,
        "updated_at": obj.updated_at.isoformat() if obj.updated_at else None,
    }


def _object_detail(obj: SiteObject, db: Session) -> dict:
    """Full detail with workers, photos, segments"""
    base = _object_to_dict(obj, db)

    # Active workers with details
    active_shifts = (
        db.query(Shift)
        .filter(Shift.site_object_id == obj.id, Shift.status == "active")
        .all()
    )
    active_workers = []
    for s in active_shifts:
        user = db.query(User).filter(User.id == s.user_id).first()
        if user:
            active_workers.append({
                "id": str(user.id),
                "name": user.full_name,
                "role": user.role or "installer",
                "shift_start": s.start_at.isoformat() if s.start_at else None,
            })

    # Recent photos (last 50)
    recent_photos = (
        db.query(ShiftPhoto)
        .filter(ShiftPhoto.site_object_id == obj.id)
        .order_by(ShiftPhoto.created_at.desc())
        .limit(50)
        .all()
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

    # Today segments
    now = datetime.now(timezone.utc)
    today_start = now.replace(hour=0, minute=0, second=0, microsecond=0)
    segments = (
        db.query(ShiftSegment)
        .filter(ShiftSegment.site_object_id == obj.id, ShiftSegment.started_at >= today_start)
        .order_by(ShiftSegment.started_at.desc())
        .limit(50)
        .all()
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

    # Reports (coordinator reports for this object)
    reports_list = []
    rows = db.execute(
        text("""
            SELECT id, report_date, content, status, photo_urls, curator_feedback, created_at
            FROM coordinator_reports
            WHERE site_object_id = :oid
            ORDER BY created_at DESC
            LIMIT 50
        """),
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

    base["active_workers"] = active_workers
    base["recent_photos"] = photos_list
    base["segments_today"] = segments_list
    base["reports"] = reports_list
    return base


# ============= Endpoints =============

@router.get("/objects")
def list_objects(
    status: Optional[str] = Query(None, description="Filter: active/completed/archived"),
    search: Optional[str] = Query(None, description="Search by name/address"),
    coordinator_id: Optional[UUID] = Query(None),
    limit: int = Query(100, ge=1, le=500),
    offset: int = Query(0, ge=0),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список всех объектов (глобальный, виден всем авторизованным)"""
    query = db.query(SiteObject)

    if status:
        query = query.filter(SiteObject.status == status)
    else:
        # По умолчанию не показываем archived
        query = query.filter(SiteObject.status != "archived")

    if search:
        pattern = f"%{search}%"
        query = query.filter(
            (SiteObject.name.ilike(pattern)) | (SiteObject.address.ilike(pattern))
        )

    if coordinator_id:
        query = query.filter(SiteObject.coordinator_id == coordinator_id)

    objects = query.order_by(SiteObject.created_at.desc()).offset(offset).limit(limit).all()

    return [_object_to_dict(obj, db) for obj in objects]


@router.post("/objects")
def create_object(
    req: CreateObjectRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Создать объект (foreman, coordinator, curator)"""
    allowed_roles = ["foreman", "coordinator", "curator"]
    if (current_user.role or "").lower() not in allowed_roles:
        raise HTTPException(status_code=403, detail="Only foreman/coordinator/curator can create objects")

    obj = SiteObject(
        name=req.name,
        address=req.address,
        description=req.description,
        created_by=current_user.id,
        coordinator_id=req.coordinator_id,
    )
    db.add(obj)
    db.commit()
    db.refresh(obj)

    return _object_to_dict(obj, db)


@router.get("/objects/{object_id}")
def get_object_detail(
    object_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Детали объекта с работниками, фото, сегментами"""
    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    return _object_detail(obj, db)


@router.put("/objects/{object_id}")
def update_object(
    object_id: UUID,
    req: UpdateObjectRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Обновить объект"""
    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    if req.name is not None:
        obj.name = req.name
    if req.address is not None:
        obj.address = req.address
    if req.description is not None:
        obj.description = req.description
    if req.measurements is not None:
        obj.measurements = req.measurements
    if req.comments is not None:
        obj.comments = req.comments
    if req.status is not None:
        if req.status not in ("active", "completed", "archived"):
            raise HTTPException(status_code=400, detail="Invalid status")
        obj.status = req.status
    if req.photo_urls is not None:
        obj.photo_urls = req.photo_urls
    if req.file_urls is not None:
        obj.file_urls = req.file_urls
    if req.coordinator_id is not None:
        obj.coordinator_id = req.coordinator_id

    db.commit()
    db.refresh(obj)

    return _object_to_dict(obj, db)


@router.delete("/objects/{object_id}")
def delete_object(
    object_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Архивировать объект (только куратор)"""
    if (current_user.role or "").lower() != "curator":
        raise HTTPException(status_code=403, detail="Only curator can archive objects")

    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    obj.status = "archived"
    db.commit()

    return {"success": True, "status": "archived"}


@router.post("/objects/{object_id}/photos")
async def upload_object_photo(
    object_id: UUID,
    photo: UploadFile = File(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Загрузить фото объекта"""
    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    content = await photo.read()
    if not content:
        raise HTTPException(status_code=400, detail="Empty file")

    photo_url = await save_shift_photo(content, photo.filename)

    urls = list(obj.photo_urls or [])
    urls.append(photo_url)
    obj.photo_urls = urls
    db.commit()

    return {"photo_url": photo_url, "total_photos": len(urls)}


@router.post("/objects/{object_id}/files")
async def upload_object_file(
    object_id: UUID,
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Загрузить файл объекта"""
    obj = db.query(SiteObject).filter(SiteObject.id == object_id).first()
    if not obj:
        raise HTTPException(status_code=404, detail="Object not found")

    content = await file.read()
    if not content:
        raise HTTPException(status_code=400, detail="Empty file")

    file_url = await save_shift_photo(content, file.filename)

    urls = list(obj.file_urls or [])
    urls.append(file_url)
    obj.file_urls = urls
    db.commit()

    return {"file_url": file_url, "total_files": len(urls)}


# ============= Shift Object Management =============

@router.post("/shifts/change-object")
def change_shift_object(
    req: ChangeObjectRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Сменить объект на активной смене (создаёт новый сегмент)"""
    # Find active shift
    shift = (
        db.query(Shift)
        .filter(Shift.user_id == current_user.id, Shift.status == "active")
        .first()
    )
    if not shift:
        raise HTTPException(status_code=400, detail="No active shift")

    # Verify new object exists
    new_obj = db.query(SiteObject).filter(SiteObject.id == req.site_object_id).first()
    if not new_obj:
        raise HTTPException(status_code=404, detail="Object not found")

    now = datetime.now(timezone.utc)

    # End current segment (if any)
    active_segment = (
        db.query(ShiftSegment)
        .filter(ShiftSegment.shift_id == shift.id, ShiftSegment.ended_at == None)
        .first()
    )
    if active_segment:
        active_segment.ended_at = now

    # Create new segment
    new_segment = ShiftSegment(
        shift_id=shift.id,
        site_object_id=req.site_object_id,
        started_at=now,
    )
    db.add(new_segment)

    # Update shift's current object
    shift.site_object_id = req.site_object_id

    db.commit()

    return {
        "success": True,
        "segment_id": str(new_segment.id),
        "site_object_id": str(req.site_object_id),
        "site_object_name": new_obj.name,
    }
