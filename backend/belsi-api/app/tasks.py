from __future__ import annotations

from datetime import datetime
from typing import Optional, List, Literal, Dict, Any
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User
from .push_notifications import send_task_notification, send_task_status_notification

router = APIRouter(prefix="/tasks", tags=["tasks"])


# -----------------------
# Schemas
# -----------------------
TaskStatus = Literal["new", "in_progress", "done", "cancelled"]
TaskPriority = Literal["low", "normal", "high", "urgent"]


class TaskOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    created_by: UUID
    assigned_to: UUID

    title: str
    description: Optional[str] = None

    status: str
    priority: str

    due_at: Optional[datetime] = None
    meta: Dict[str, Any]

    created_at: datetime
    updated_at: datetime


class TaskCreate(BaseModel):
    title: str = Field(min_length=1, max_length=200)
    description: Optional[str] = Field(None, max_length=4000)
    assigned_to: UUID
    priority: Optional[TaskPriority] = "normal"
    due_at: Optional[datetime] = None
    meta: Optional[Dict[str, Any]] = None


class TaskPatch(BaseModel):
    status: Optional[TaskStatus] = None
    title: Optional[str] = Field(None, max_length=200)
    description: Optional[str] = Field(None, max_length=4000)
    priority: Optional[TaskPriority] = None
    due_at: Optional[datetime] = None
    meta: Optional[Dict[str, Any]] = None


# -----------------------
# Helpers
# -----------------------
def require_role(user: User, allowed: set[str]) -> None:
    role = (getattr(user, "role", "") or "").strip().lower()
    if role not in allowed:
        raise HTTPException(status_code=403, detail="Forbidden")


def foreman_can_assign(db: Session, foreman_id: UUID, installer_id: UUID) -> bool:
    row = db.execute(
        text(
            """
            SELECT 1
            FROM public.foreman_memberships
            WHERE foreman_user_id = :f AND installer_user_id = :i
            LIMIT 1
            """
        ),
        {"f": str(foreman_id), "i": str(installer_id)},
    ).first()
    return row is not None


def fetch_task_or_404(db: Session, task_id: UUID) -> dict:
    row = db.execute(
        text("SELECT * FROM public.tasks WHERE id = :id"),
        {"id": str(task_id)},
    ).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Task not found")
    return dict(row)


# -----------------------
# API
# -----------------------
@router.post("", response_model=TaskOut)
def create_task(
    payload: TaskCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    role = (current_user.role or "").lower()
    require_role(current_user, {"foreman", "curator"})

    assignee = db.execute(
        text("SELECT id, role FROM public.users WHERE id = :id"),
        {"id": str(payload.assigned_to)},
    ).mappings().first()
    if not assignee:
        raise HTTPException(status_code=404, detail="Assigned user not found")

    if role == "foreman":
        if not foreman_can_assign(db, current_user.id, payload.assigned_to):
            raise HTTPException(status_code=403, detail="You can assign tasks only to your installers")

    meta = payload.meta or {}

    row = db.execute(
        text(
            """
            INSERT INTO public.tasks (created_by, assigned_to, title, description, status, priority, due_at, meta)
            VALUES (:created_by, :assigned_to, :title, :description, 'new', :priority, :due_at, CAST(:meta AS jsonb))
            RETURNING *
            """
        ),
        {
            "created_by": str(current_user.id),
            "assigned_to": str(payload.assigned_to),
            "title": payload.title,
            "description": payload.description,
            "priority": payload.priority or "normal",
            "due_at": payload.due_at,
            "meta": __import__("json").dumps(meta),
        },
    ).mappings().first()

    db.commit()
    
    try:
        creator_name = ((current_user.first_name or "") + " " + (current_user.last_name or "")).strip() or current_user.phone
        send_task_notification(
            user_id=payload.assigned_to,
            task_id=str(row["id"]),
            task_title=payload.title,
            assigned_by_name=creator_name,
            priority=payload.priority or "normal",
            description=payload.description or "",
            db=db
        )
    except Exception as e:
        import logging
        logging.error(f"Failed to send task notification: {e}")
        
    return TaskOut(**row)


@router.get("/my", response_model=List[TaskOut])
def my_tasks(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    status: Optional[TaskStatus] = Query(None),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
):
    sql = "SELECT * FROM public.tasks WHERE assigned_to = :uid"
    params = {"uid": str(current_user.id)}

    if status:
        sql += " AND status = :status"
        params["status"] = status

    sql += " ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
    params["limit"] = limit
    params["offset"] = offset

    rows = db.execute(text(sql), params).mappings().all()
    return [TaskOut(**r) for r in rows]


@router.get("/created", response_model=List[TaskOut])
def created_by_me(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    status: Optional[TaskStatus] = Query(None),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
):
    require_role(current_user, {"foreman", "curator"})

    sql = "SELECT * FROM public.tasks WHERE created_by = :uid"
    params = {"uid": str(current_user.id)}

    if status:
        sql += " AND status = :status"
        params["status"] = status

    sql += " ORDER BY created_at DESC LIMIT :limit OFFSET :offset"
    params["limit"] = limit
    params["offset"] = offset

    rows = db.execute(text(sql), params).mappings().all()
    return [TaskOut(**r) for r in rows]


@router.get("/{task_id}", response_model=TaskOut)
def get_task(
    task_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    task = fetch_task_or_404(db, task_id)

    role = (current_user.role or "").lower()
    if role != "curator" and str(task["created_by"]) != str(current_user.id) and str(task["assigned_to"]) != str(current_user.id):
        raise HTTPException(status_code=403, detail="Forbidden")

    return TaskOut(**task)


@router.patch("/{task_id}", response_model=TaskOut)
def patch_task(
    task_id: UUID,
    payload: TaskPatch,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    task = fetch_task_or_404(db, task_id)

    role = (current_user.role or "").lower()
    is_creator = str(task["created_by"]) == str(current_user.id)
    is_assignee = str(task["assigned_to"]) == str(current_user.id)

    if role != "curator" and not (is_creator or is_assignee):
        raise HTTPException(status_code=403, detail="Forbidden")

    fields = []
    params = {"id": str(task_id)}

    if payload.status is not None:
        if role == "curator" or is_creator or is_assignee:
            fields.append("status = :status")
            params["status"] = payload.status
        else:
            raise HTTPException(status_code=403, detail="Forbidden")

    if payload.title is not None:
        if role == "curator" or is_creator:
            fields.append("title = :title")
            params["title"] = payload.title
        else:
            raise HTTPException(status_code=403, detail="Only creator can change title")

    if payload.description is not None:
        if role == "curator" or is_creator:
            fields.append("description = :description")
            params["description"] = payload.description
        else:
            raise HTTPException(status_code=403, detail="Only creator can change description")

    if payload.priority is not None:
        if role == "curator" or is_creator:
            fields.append("priority = :priority")
            params["priority"] = payload.priority
        else:
            raise HTTPException(status_code=403, detail="Only creator can change priority")

    if payload.due_at is not None:
        if role == "curator" or is_creator:
            fields.append("due_at = :due_at")
            params["due_at"] = payload.due_at
        else:
            raise HTTPException(status_code=403, detail="Only creator can change due date")

    if payload.meta is not None:
        if role == "curator" or is_creator:
            fields.append("meta = CAST(:meta AS jsonb)")
            params["meta"] = __import__("json").dumps(payload.meta)
        else:
            raise HTTPException(status_code=403, detail="Only creator can change meta")

    if not fields:
        return TaskOut(**task)

    sql = f"""
      UPDATE public.tasks
      SET {", ".join(fields)}, updated_at = now()
      WHERE id = :id
      RETURNING *
    """

    row = db.execute(text(sql), params).mappings().first()
    db.commit()
    
    # Notify creator when assignee changes status
    if payload.status is not None and is_assignee:
        try:
            assignee_name = ((current_user.first_name or "") + " " + (current_user.last_name or "")).strip() or current_user.phone
            send_task_status_notification(
                user_id=UUID(str(task["created_by"])),
                task_title=task["title"],
                new_status=payload.status,
                changed_by_name=assignee_name,
                db=db
            )
        except Exception as e:
            import logging
            logging.error(f"Failed to send task status notification: {e}")
    
    return TaskOut(**row)
