"""
Engineer tasks — инженерные задачи.

Engineer-роль работает с тех. сопровождением партий:
- Дизайн / Tooling / Quality check / Tech doc / Repair
- Эскалация партий с проблемами
- Связь с конкретной партией или фабрикой
"""
from __future__ import annotations
from datetime import datetime, timezone
from typing import Optional, List
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User

router = APIRouter(prefix="/production/engineer", tags=["production-engineer"])


class EngineerTaskOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    batch_id: Optional[UUID] = None
    batch_title: Optional[str] = None
    facility_id: Optional[UUID] = None
    facility_name: Optional[str] = None
    type: str
    title: str
    description: Optional[str] = None
    assigned_to: Optional[UUID] = None
    assigned_to_name: Optional[str] = None
    status: str
    priority: str
    due_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    created_by: UUID
    created_by_name: Optional[str] = None
    created_at: datetime
    updated_at: datetime


class EngineerTaskCreate(BaseModel):
    type: str = "general"
    title: str
    description: Optional[str] = None
    batch_id: Optional[UUID] = None
    facility_id: Optional[UUID] = None
    assigned_to: Optional[UUID] = None
    priority: str = "normal"
    due_at: Optional[datetime] = None


class EngineerTaskStatusUpdate(BaseModel):
    status: str  # in_progress / done / cancelled


@router.get("/tasks", response_model=List[EngineerTaskOut])
def list_tasks(
    mine: bool = Query(False, description="Только мои назначенные"),
    status: Optional[str] = Query(None),
    type: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Список инженерных задач.
    - Engineer: видит свои назначенные (mine=true) или все open (по дефолту)
    - Chief / Curator: видит всё
    """
    where = []
    params = {}
    if mine:
        where.append("e.assigned_to = :uid")
        params["uid"] = str(current_user.id)
    elif current_user.role == "engineer":
        # Engineer без mine видит свои + неназначенные
        where.append("(e.assigned_to = :uid OR e.assigned_to IS NULL)")
        params["uid"] = str(current_user.id)

    if status:
        where.append("e.status = :status")
        params["status"] = status
    if type:
        where.append("e.type = :type")
        params["type"] = type

    where_sql = ("WHERE " + " AND ".join(where)) if where else ""

    rows = db.execute(
        text(f"""
            SELECT e.*,
                   b.title AS batch_title,
                   f.name AS facility_name,
                   (au.first_name || ' ' || COALESCE(au.last_name, '')) AS assigned_to_name,
                   (cu.first_name || ' ' || COALESCE(cu.last_name, '')) AS created_by_name
            FROM engineer_tasks e
            LEFT JOIN production_batches b ON b.id = e.batch_id
            LEFT JOIN site_objects f ON f.id = e.facility_id
            LEFT JOIN users au ON au.id = e.assigned_to
            LEFT JOIN users cu ON cu.id = e.created_by
            {where_sql}
            ORDER BY
                CASE e.priority WHEN 'urgent' THEN 0 WHEN 'high' THEN 1 WHEN 'normal' THEN 2 ELSE 3 END,
                e.created_at DESC
            LIMIT 200
        """),
        params,
    ).mappings().all()
    return [EngineerTaskOut(**dict(r)) for r in rows]


@router.post("/tasks", response_model=EngineerTaskOut, status_code=201)
def create_task(
    payload: EngineerTaskCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Создать инженерную задачу. Создавать могут все production-роли + curator."""
    valid_creators = {"engineer", "production_chief", "senior_worker", "curator", "coordinator"}
    if current_user.role not in valid_creators:
        raise HTTPException(status_code=403, detail="Недостаточно прав")

    row = db.execute(
        text("""
            INSERT INTO engineer_tasks (
                batch_id, facility_id, type, title, description,
                assigned_to, priority, due_at, created_by
            ) VALUES (
                :bid, :fid, :type, :title, :description,
                :assigned, :priority, :due, :creator
            )
            RETURNING id
        """),
        {
            "bid": str(payload.batch_id) if payload.batch_id else None,
            "fid": str(payload.facility_id) if payload.facility_id else None,
            "type": payload.type,
            "title": payload.title,
            "description": payload.description,
            "assigned": str(payload.assigned_to) if payload.assigned_to else None,
            "priority": payload.priority,
            "due": payload.due_at,
            "creator": str(current_user.id),
        },
    ).mappings().first()
    db.commit()

    full = db.execute(
        text("""
            SELECT e.*,
                   b.title AS batch_title,
                   f.name AS facility_name,
                   (au.first_name || ' ' || COALESCE(au.last_name, '')) AS assigned_to_name,
                   (cu.first_name || ' ' || COALESCE(cu.last_name, '')) AS created_by_name
            FROM engineer_tasks e
            LEFT JOIN production_batches b ON b.id = e.batch_id
            LEFT JOIN site_objects f ON f.id = e.facility_id
            LEFT JOIN users au ON au.id = e.assigned_to
            LEFT JOIN users cu ON cu.id = e.created_by
            WHERE e.id = :eid
        """),
        {"eid": str(row["id"])},
    ).mappings().first()
    return EngineerTaskOut(**dict(full))


@router.patch("/tasks/{task_id}/status", response_model=EngineerTaskOut)
def update_task_status(
    task_id: UUID,
    payload: EngineerTaskStatusUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Сменить статус инженерной задачи."""
    valid = {"open", "in_progress", "done", "cancelled"}
    if payload.status not in valid:
        raise HTTPException(status_code=400, detail=f"Статус: {valid}")

    completed = "completed_at = NOW()" if payload.status == "done" else "completed_at = NULL"

    row = db.execute(
        text(f"""
            UPDATE engineer_tasks
            SET status = :status, updated_at = NOW(), {completed}
            WHERE id = :tid
            RETURNING id
        """),
        {"tid": str(task_id), "status": payload.status},
    ).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Задача не найдена")
    db.commit()

    full = db.execute(
        text("""
            SELECT e.*,
                   b.title AS batch_title,
                   f.name AS facility_name,
                   (au.first_name || ' ' || COALESCE(au.last_name, '')) AS assigned_to_name,
                   (cu.first_name || ' ' || COALESCE(cu.last_name, '')) AS created_by_name
            FROM engineer_tasks e
            LEFT JOIN production_batches b ON b.id = e.batch_id
            LEFT JOIN site_objects f ON f.id = e.facility_id
            LEFT JOIN users au ON au.id = e.assigned_to
            LEFT JOIN users cu ON cu.id = e.created_by
            WHERE e.id = :tid
        """),
        {"tid": str(task_id)},
    ).mappings().first()
    return EngineerTaskOut(**dict(full))


@router.get("/tools-catalog", response_model=List[dict])
def list_engineer_tools(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Каталог инженерных инструментов.
    Используем существующую таблицу `tools` с фильтром по category='engineer'.
    """
    rows = db.execute(
        text("""
            SELECT id, name, status, category, condition
            FROM tools
            WHERE COALESCE(category, '') IN ('', 'engineer', 'measurement', 'electrical')
            ORDER BY name
            LIMIT 100
        """),
    ).mappings().all()
    return [dict(r) for r in rows]
