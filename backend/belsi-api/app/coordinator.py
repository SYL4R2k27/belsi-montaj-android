"""
Функционал координатора объекта.
Координатор закреплён за site_object и видит только данных «своего» объекта.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query, Body
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime, timezone
from typing import List, Optional
from uuid import UUID, uuid4
from sqlalchemy.orm import Session
from sqlalchemy import text, func

from .db import get_db
from .auth import get_current_user
from .models import User, Shift, ShiftPhoto, Task

router = APIRouter(prefix="/coordinator", tags=["coordinator"])


# ============= Helpers =============

def require_coordinator(user: User):
    role = (user.role or "").strip().lower()
    if role != "coordinator":
        raise HTTPException(status_code=403, detail="Coordinator access required")


def _get_site(db: Session, coordinator_id):
    """Получить site_object координатора (raw SQL — модели может не быть)."""
    row = db.execute(
        text("SELECT id, name, address, status, measurements, comments, created_at, updated_at "
             "FROM site_objects WHERE coordinator_id = :cid LIMIT 1"),
        {"cid": str(coordinator_id)},
    ).mappings().first()
    return row


# ============= Schemas =============

class CoordinatorSiteOut(BaseModel):
    id: str
    name: str = ""
    address: Optional[str] = None
    status: str = "active"
    measurements: dict = {}
    comments: Optional[str] = None
    created_at: Optional[str] = None
    updated_at: Optional[str] = None


class CoordinatorDashboardOut(BaseModel):
    site_name: Optional[str] = None
    site_address: Optional[str] = None
    site_status: str = "no_site"
    active_shift: bool = False
    shift_duration_seconds: int = 0
    total_foremen: int = 0
    total_installers: int = 0
    active_workers_today: int = 0
    pending_photos: int = 0
    total_photos_today: int = 0
    tasks_total: int = 0
    tasks_completed: int = 0
    reports_today: int = 0


class CoordinatorPhotoOut(BaseModel):
    id: str
    user_id: Optional[str] = None
    user_phone: Optional[str] = None
    user_name: Optional[str] = None
    user_role: str = "installer"
    photo_url: str = ""
    shift_id: Optional[str] = None
    timestamp: Optional[str] = None
    status: Optional[str] = None
    comment: Optional[str] = None
    category: str = "hourly"
    ai_comment: Optional[str] = None


class CoordinatorTeamMemberOut(BaseModel):
    id: str
    phone: str = ""
    full_name: Optional[str] = None
    role: str = "installer"
    is_active_today: bool = False
    current_shift_status: Optional[str] = None
    shift_duration_seconds: int = 0
    photos_today: int = 0
    team_size: int = 0


class CoordinatorTaskOut(BaseModel):
    id: str
    title: str = ""
    description: Optional[str] = None
    status: str = "new"
    priority: str = "normal"
    assigned_to: str = ""
    assigned_name: Optional[str] = None
    created_by: str = ""
    creator_name: Optional[str] = None
    due_at: Optional[str] = None
    created_at: Optional[str] = None


class CoordinatorReportOut(BaseModel):
    id: str
    report_date: Optional[str] = None
    content: str = ""
    status: str = "submitted"
    photo_urls: List[str] = []
    created_at: Optional[str] = None
    updated_at: Optional[str] = None


# ============= Endpoints =============

@router.get("/dashboard")
def coordinator_dashboard(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    site = _get_site(db, current_user.id)
    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)

    # Текущая смена координатора
    own_shift = (
        db.query(Shift)
        .filter(Shift.user_id == current_user.id, Shift.finish_at == None)
        .first()
    )
    shift_duration = 0
    if own_shift and own_shift.start_at:
        start = own_shift.start_at if own_shift.start_at.tzinfo else own_shift.start_at.replace(tzinfo=timezone.utc)
        shift_duration = int((datetime.now(timezone.utc) - start).total_seconds())

    # Считаем работников на объекте (все foreman+installer)
    total_foremen = db.query(func.count(User.id)).filter(User.role == "foreman").scalar() or 0
    total_installers = db.query(func.count(User.id)).filter(User.role == "installer").scalar() or 0

    # Активные сегодня
    active_today = (
        db.query(func.count(func.distinct(Shift.user_id)))
        .filter(Shift.start_at >= today_start)
        .scalar() or 0
    )

    # Фото
    pending = db.query(func.count(ShiftPhoto.id)).filter(ShiftPhoto.status == "pending").scalar() or 0
    photos_today = (
        db.query(func.count(ShiftPhoto.id))
        .filter(ShiftPhoto.created_at >= today_start)
        .scalar() or 0
    )

    # Задачи
    tasks_total = db.query(func.count(Task.id)).scalar() or 0
    tasks_completed = db.query(func.count(Task.id)).filter(Task.status == "done").scalar() or 0

    # Отчёты сегодня
    reports_today = 0
    if site:
        rpt = db.execute(
            text("SELECT COUNT(*) FROM coordinator_reports WHERE site_object_id = :sid AND report_date = CURRENT_DATE"),
            {"sid": str(site["id"])},
        ).scalar()
        reports_today = rpt or 0

    return {
        "site_name": site["name"] if site else None,
        "site_address": site["address"] if site else None,
        "site_status": site["status"] if site else "no_site",
        "active_shift": own_shift is not None,
        "shift_duration_seconds": shift_duration,
        "total_foremen": total_foremen,
        "total_installers": total_installers,
        "active_workers_today": active_today,
        "pending_photos": pending,
        "total_photos_today": photos_today,
        "tasks_total": tasks_total,
        "tasks_completed": tasks_completed,
        "reports_today": reports_today,
    }


# ---------- Photos ----------

@router.get("/photos")
def coordinator_photos(
    status: Optional[str] = Query(None),
    category: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    query = db.query(ShiftPhoto).join(Shift, Shift.id == ShiftPhoto.shift_id)
    if status:
        query = query.filter(ShiftPhoto.status == status)
    else:
        query = query.filter(ShiftPhoto.status == "pending")
    if category:
        query = query.filter(ShiftPhoto.category == category)

    photos = query.order_by(ShiftPhoto.created_at.desc()).limit(100).all()

    result = []
    for p in photos:
        shift = db.query(Shift).filter(Shift.id == p.shift_id).first()
        user = db.query(User).filter(User.id == shift.user_id).first() if shift else None
        result.append({
            "id": str(p.id),
            "user_id": str(user.id) if user else None,
            "user_phone": user.phone if user else None,
            "user_name": getattr(user, "full_name", None) if user else None,
            "user_role": user.role if user else "installer",
            "photo_url": p.photo_url or "",
            "shift_id": str(p.shift_id),
            "timestamp": p.created_at.isoformat() if p.created_at else None,
            "status": p.status,
            "comment": p.comment,
            "category": getattr(p, "category", "hourly") or "hourly",
            "ai_comment": getattr(p, "ai_comment", None),
        })

    return {"photos": result}


@router.post("/photos/{photo_id}/approve")
def coordinator_approve_photo(
    photo_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)
    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    photo.status = "approved"
    db.commit()
    return {"success": True}


@router.post("/photos/{photo_id}/reject")
def coordinator_reject_photo(
    photo_id: UUID,
    body: dict = Body(default={}),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)
    photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if not photo:
        raise HTTPException(status_code=404, detail="Photo not found")
    photo.status = "rejected"
    comment = body.get("comment") if body else None
    if comment:
        if photo.comment:
            photo.comment = f"{photo.comment}\n[COORDINATOR]: {comment}"
        else:
            photo.comment = f"[COORDINATOR]: {comment}"
    db.commit()
    return {"success": True}


# ---------- Tasks ----------

@router.get("/tasks")
def coordinator_tasks(
    status: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    query = db.query(Task)
    if status:
        query = query.filter(Task.status == status)
    tasks = query.order_by(Task.created_at.desc()).limit(200).all()

    result = []
    for t in tasks:
        assigned_user = db.query(User).filter(User.id == t.assigned_to).first()
        creator = db.query(User).filter(User.id == t.created_by).first()
        result.append({
            "id": str(t.id),
            "title": t.title or "",
            "description": t.description,
            "status": t.status or "new",
            "priority": t.priority or "normal",
            "assigned_to": str(t.assigned_to) if t.assigned_to else "",
            "assigned_name": getattr(assigned_user, "full_name", None) if assigned_user else None,
            "created_by": str(t.created_by) if t.created_by else "",
            "creator_name": getattr(creator, "full_name", None) if creator else None,
            "due_at": t.due_at.isoformat() if hasattr(t, "due_at") and t.due_at else None,
            "created_at": t.created_at.isoformat() if t.created_at else None,
        })

    return {"tasks": result}


@router.post("/tasks")
def coordinator_create_task(
    body: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    title = body.get("title")
    if not title:
        raise HTTPException(status_code=400, detail="title is required")

    assigned_to = body.get("assigned_to")
    if not assigned_to:
        raise HTTPException(status_code=400, detail="assigned_to is required")

    assigned_user = db.query(User).filter(User.id == assigned_to).first()
    if not assigned_user:
        raise HTTPException(status_code=404, detail="Assigned user not found")

    new_task = Task(
        id=uuid4(),
        created_by=current_user.id,
        assigned_to=assigned_user.id,
        title=title,
        description=body.get("description"),
        priority=body.get("priority", "normal"),
        status="new",
    )
    db.add(new_task)
    db.commit()
    return {"success": True, "task_id": str(new_task.id)}


# ---------- Team ----------

@router.get("/team")
def coordinator_team(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)

    # Все бригадиры и монтажники
    workers = db.query(User).filter(User.role.in_(["foreman", "installer"])).all()

    result = []
    for w in workers:
        # Текущая смена
        current_shift = (
            db.query(Shift)
            .filter(Shift.user_id == w.id, Shift.finish_at == None)
            .first()
        )
        shift_status = None
        shift_duration = 0
        if current_shift:
            shift_status = current_shift.status
            if current_shift.start_at:
                start = current_shift.start_at if current_shift.start_at.tzinfo else current_shift.start_at.replace(tzinfo=timezone.utc)
                shift_duration = int((datetime.now(timezone.utc) - start).total_seconds())

        # Активность сегодня
        shift_today = db.query(Shift).filter(Shift.user_id == w.id, Shift.start_at >= today_start).first()

        # Фото сегодня
        photos_today = (
            db.query(func.count(ShiftPhoto.id))
            .join(Shift)
            .filter(Shift.user_id == w.id, ShiftPhoto.created_at >= today_start)
            .scalar() or 0
        )

        # Размер команды (для бригадиров)
        team_size = 0
        if w.role == "foreman":
            ts = db.execute(
                text("SELECT COUNT(*) FROM foreman_memberships WHERE foreman_user_id = :fid AND status = 'active'"),
                {"fid": str(w.id)},
            ).scalar()
            team_size = ts or 0

        result.append({
            "id": str(w.id),
            "phone": w.phone,
            "full_name": getattr(w, "full_name", None) or w.phone,
            "role": w.role or "installer",
            "is_active_today": shift_today is not None,
            "current_shift_status": shift_status,
            "shift_duration_seconds": shift_duration,
            "photos_today": photos_today,
            "team_size": team_size,
        })

    return {"team": result}


# ---------- Reports ----------

@router.get("/reports")
def coordinator_reports(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    site = _get_site(db, current_user.id)
    if not site:
        return {"reports": []}

    rows = db.execute(
        text("SELECT id, report_date, content, status, photo_urls, created_at, updated_at "
             "FROM coordinator_reports WHERE coordinator_id = :cid ORDER BY created_at DESC LIMIT 100"),
        {"cid": str(current_user.id)},
    ).mappings().all()

    result = []
    for r in rows:
        result.append({
            "id": str(r["id"]),
            "report_date": str(r["report_date"]) if r["report_date"] else None,
            "content": r["content"] or "",
            "status": r["status"] or "submitted",
            "photo_urls": r["photo_urls"] or [],
            "created_at": r["created_at"].isoformat() if r["created_at"] else None,
            "updated_at": r["updated_at"].isoformat() if r["updated_at"] else None,
        })

    return {"reports": result}


@router.post("/reports")
def coordinator_create_report(
    body: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    site = _get_site(db, current_user.id)
    if not site:
        raise HTTPException(status_code=404, detail="No site assigned to coordinator")

    content = body.get("content")
    if not content:
        raise HTTPException(status_code=400, detail="content is required")

    import json
    photo_urls = body.get("photo_urls", [])
    report_date = body.get("report_date")

    db.execute(
        text("""
            INSERT INTO coordinator_reports (id, coordinator_id, site_object_id, report_date, content, status, photo_urls, created_at, updated_at)
            VALUES (gen_random_uuid(), :cid, :sid, COALESCE(CAST(:rd AS date), CURRENT_DATE), :content, 'submitted', CAST(:photos AS jsonb), NOW(), NOW())
        """),
        {
            "cid": str(current_user.id),
            "sid": str(site["id"]),
            "rd": report_date,
            "content": content,
            "photos": json.dumps(photo_urls),
        },
    )
    db.commit()
    return {"success": True}


@router.put("/reports/{report_id}")
def coordinator_update_report(
    report_id: UUID,
    body: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    import json
    updates = []
    params = {"rid": str(report_id), "cid": str(current_user.id)}

    if "content" in body and body["content"] is not None:
        updates.append("content = :content")
        params["content"] = body["content"]
    if "status" in body and body["status"] is not None:
        updates.append("status = :status")
        params["status"] = body["status"]
    if "photo_urls" in body and body["photo_urls"] is not None:
        updates.append("photo_urls = CAST(:photos AS jsonb)")
        params["photos"] = json.dumps(body["photo_urls"])

    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")

    updates.append("updated_at = NOW()")
    set_clause = ", ".join(updates)

    result = db.execute(
        text(f"UPDATE coordinator_reports SET {set_clause} WHERE id = :rid AND coordinator_id = :cid"),
        params,
    )
    db.commit()

    if result.rowcount == 0:
        raise HTTPException(status_code=404, detail="Report not found")

    return {"success": True}


# ---------- Site ----------

@router.get("/site")
def coordinator_get_site(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    site = _get_site(db, current_user.id)
    if not site:
        return {"site": None}

    return {
        "site": {
            "id": str(site["id"]),
            "name": site["name"] or "",
            "address": site["address"],
            "status": site["status"] or "active",
            "measurements": site["measurements"] or {},
            "comments": site["comments"],
            "created_at": site["created_at"].isoformat() if site["created_at"] else None,
            "updated_at": site["updated_at"].isoformat() if site["updated_at"] else None,
        }
    }


@router.put("/site")
def coordinator_update_site(
    body: dict = Body(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    require_coordinator(current_user)

    site = _get_site(db, current_user.id)
    if not site:
        raise HTTPException(status_code=404, detail="No site assigned to coordinator")

    import json
    updates = []
    params = {"sid": str(site["id"])}

    if "measurements" in body and body["measurements"] is not None:
        updates.append("measurements = CAST(:meas AS jsonb)")
        params["meas"] = json.dumps(body["measurements"])
    if "comments" in body and body["comments"] is not None:
        updates.append("comments = :comments")
        params["comments"] = body["comments"]
    if "status" in body and body["status"] is not None:
        updates.append("status = :status")
        params["status"] = body["status"]

    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")

    updates.append("updated_at = NOW()")
    set_clause = ", ".join(updates)

    db.execute(text(f"UPDATE site_objects SET {set_clause} WHERE id = :sid"), params)
    db.commit()
    return {"success": True}
