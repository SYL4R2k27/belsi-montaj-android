"""
FIX(2026-05-05): API для Pipeline партии в экосистеме BELSI.

Партия — единица товара, проходящая по цепочке:
    draft → in_production → ready_to_ship → in_route → delivered → installed

Доступ:
- Начальник производства (production_chief) — CRUD на свою фабрику
- Старший работник (senior_worker) — read-only по своей фабрике
- Логист (logistician) — видит ready_to_ship, переводит в in_route
- Бригадир / Координатор — видят свои (target_object_id), подтверждают delivery / закрывают
- Куратор — видит всё

ЛОКАЛЬНО (НЕ ЗАДЕПЛОЕНО) — этот файл существует только в репо разработчика,
прод (api.belsi.ru) пока этих endpoints не имеет. Будут включены после ревью.
"""
from __future__ import annotations

import logging
from datetime import datetime
from typing import List, Literal, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import text
from sqlalchemy.orm import Session

from .auth import get_current_user
from .db import get_db
from .models import User, ProductionBatch, BatchStatusHistory

router = APIRouter(prefix="/production/batches", tags=["production-batches"])
log = logging.getLogger("production_batches")

# Допустимые статусы и переходы
ALL_STATUSES = (
    "draft",
    "in_production",
    "ready_to_ship",
    "in_route",
    "delivered",
    "installed",
    "cancelled",
)

# Граф разрешённых переходов: from -> set of allowed to
TRANSITIONS = {
    "draft": {"in_production", "cancelled"},
    "in_production": {"ready_to_ship", "cancelled"},
    "ready_to_ship": {"in_route", "in_production", "cancelled"},
    "in_route": {"delivered", "cancelled"},
    "delivered": {"installed"},
    "installed": set(),  # терминальный
    "cancelled": set(),
}

# Кто может менять какой переход (по роли)
TRANSITION_PERMISSIONS = {
    ("draft", "in_production"): {"production_chief", "senior_worker", "curator"},
    ("draft", "cancelled"): {"production_chief", "curator"},
    ("in_production", "ready_to_ship"): {"production_chief", "senior_worker", "curator"},
    ("in_production", "cancelled"): {"production_chief", "curator"},
    ("ready_to_ship", "in_route"): {"logistician", "curator"},
    ("ready_to_ship", "in_production"): {"production_chief", "curator"},
    ("ready_to_ship", "cancelled"): {"production_chief", "logistician", "curator"},
    ("in_route", "delivered"): {"driver", "foreman", "coordinator", "curator"},
    ("in_route", "cancelled"): {"logistician", "curator"},
    ("delivered", "installed"): {"foreman", "coordinator", "curator"},
}


# ─────────────── Pydantic schemas ───────────────

BatchStatus = Literal[
    "draft", "in_production", "ready_to_ship",
    "in_route", "delivered", "installed", "cancelled",
]


class BatchCreate(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    description: Optional[str] = Field(None, max_length=4000)
    item_count: int = Field(0, ge=0)
    source_facility_id: UUID
    target_object_id: Optional[UUID] = None
    priority: str = "normal"
    deadline: Optional[datetime] = None
    responsible_user_id: Optional[UUID] = None


class BatchPatch(BaseModel):
    title: Optional[str] = Field(None, max_length=300)
    description: Optional[str] = Field(None, max_length=4000)
    item_count: Optional[int] = Field(None, ge=0)
    target_object_id: Optional[UUID] = None
    priority: Optional[str] = None
    deadline: Optional[datetime] = None
    responsible_user_id: Optional[UUID] = None


class BatchStatusChange(BaseModel):
    to_status: BatchStatus
    comment: Optional[str] = None


class BatchOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    title: str
    description: Optional[str] = None
    item_count: int
    source_facility_id: UUID
    target_object_id: Optional[UUID] = None
    status: str
    priority: str
    deadline: Optional[datetime] = None
    created_by: UUID
    responsible_user_id: Optional[UUID] = None
    created_at: datetime
    updated_at: datetime


class BatchHistoryItem(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    batch_id: UUID
    from_status: Optional[str]
    to_status: str
    changed_by: UUID
    changed_at: datetime
    comment: Optional[str]


# ─────────────── Helpers ───────────────

def _user_role(u: User) -> str:
    return (getattr(u, "role", "") or "").strip().lower()


def _can_create_batch(u: User) -> bool:
    return _user_role(u) in {"production_chief", "senior_worker", "curator"}


def _can_view_all(u: User) -> bool:
    return _user_role(u) == "curator"


def _can_transition(u: User, from_status: str, to_status: str) -> bool:
    role = _user_role(u)
    allowed_roles = TRANSITION_PERMISSIONS.get((from_status, to_status), set())
    return role in allowed_roles


def _fetch_batch(db: Session, batch_id: UUID) -> ProductionBatch:
    b = db.query(ProductionBatch).filter(ProductionBatch.id == batch_id).first()
    if not b:
        raise HTTPException(status_code=404, detail="Batch not found")
    return b


# ─────────────── Endpoints ───────────────

@router.get("", response_model=List[BatchOut])
def list_batches(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    status: Optional[BatchStatus] = Query(None),
    facility_id: Optional[UUID] = Query(None),
    target_object_id: Optional[UUID] = Query(None),
    limit: int = Query(50, ge=1, le=200),
    offset: int = Query(0, ge=0),
):
    """
    Список партий с фильтрами.
    Видимость:
    - Куратор: все
    - Логист: только ready_to_ship + in_route (если без status-фильтра)
    - Production roles: только своей фабрики
    - Foreman/Coordinator: только где target_object_id = их объект
    """
    query = db.query(ProductionBatch)

    role = _user_role(current_user)
    if role == "logistician" and status is None:
        query = query.filter(ProductionBatch.status.in_(["ready_to_ship", "in_route"]))

    if status:
        query = query.filter(ProductionBatch.status == status)
    if facility_id:
        query = query.filter(ProductionBatch.source_facility_id == facility_id)
    if target_object_id:
        query = query.filter(ProductionBatch.target_object_id == target_object_id)

    rows = query.order_by(ProductionBatch.created_at.desc()).offset(offset).limit(limit).all()
    return [BatchOut.model_validate(r) for r in rows]


@router.post("", response_model=BatchOut, status_code=201)
def create_batch(
    payload: BatchCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Создать партию (Начальник производства / Старший работник / Куратор)."""
    if not _can_create_batch(current_user):
        raise HTTPException(status_code=403, detail="Only production_chief / senior_worker / curator")

    b = ProductionBatch(
        title=payload.title,
        description=payload.description,
        item_count=payload.item_count,
        source_facility_id=payload.source_facility_id,
        target_object_id=payload.target_object_id,
        status="draft",
        priority=payload.priority,
        deadline=payload.deadline,
        created_by=current_user.id,
        responsible_user_id=payload.responsible_user_id,
    )
    db.add(b)
    db.flush()

    # audit log
    db.add(BatchStatusHistory(
        batch_id=b.id,
        from_status=None,
        to_status="draft",
        changed_by=current_user.id,
    ))

    db.commit()
    db.refresh(b)
    return BatchOut.model_validate(b)


@router.get("/{batch_id}", response_model=BatchOut)
def get_batch(
    batch_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    return BatchOut.model_validate(_fetch_batch(db, batch_id))


@router.patch("/{batch_id}", response_model=BatchOut)
def patch_batch(
    batch_id: UUID,
    payload: BatchPatch,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Обновить поля партии (без смены статуса). Только в статусе draft / in_production."""
    b = _fetch_batch(db, batch_id)
    if b.status not in {"draft", "in_production"}:
        raise HTTPException(status_code=400, detail="Cannot edit batch in status " + b.status)
    if not _can_create_batch(current_user) and current_user.id != b.created_by:
        raise HTTPException(status_code=403, detail="Forbidden")

    for field, value in payload.model_dump(exclude_unset=True).items():
        setattr(b, field, value)

    db.commit()
    db.refresh(b)
    return BatchOut.model_validate(b)


@router.post("/{batch_id}/status", response_model=BatchOut)
def change_status(
    batch_id: UUID,
    payload: BatchStatusChange,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Изменить статус партии. Проверка allowed transitions + permissions."""
    b = _fetch_batch(db, batch_id)
    if payload.to_status == b.status:
        return BatchOut.model_validate(b)

    allowed = TRANSITIONS.get(b.status, set())
    if payload.to_status not in allowed:
        raise HTTPException(
            status_code=400,
            detail=f"Transition {b.status} → {payload.to_status} not allowed",
        )
    if not _can_transition(current_user, b.status, payload.to_status):
        raise HTTPException(
            status_code=403,
            detail=f"Role {_user_role(current_user)} cannot transition {b.status} → {payload.to_status}",
        )

    old_status = b.status
    b.status = payload.to_status

    db.add(BatchStatusHistory(
        batch_id=b.id,
        from_status=old_status,
        to_status=payload.to_status,
        changed_by=current_user.id,
        comment=payload.comment,
    ))

    db.commit()
    db.refresh(b)
    log.info(f"batch {b.id}: {old_status} -> {payload.to_status} by user {current_user.id}")
    return BatchOut.model_validate(b)


@router.get("/{batch_id}/history", response_model=List[BatchHistoryItem])
def get_history(
    batch_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """История смен статусов (audit log)."""
    _fetch_batch(db, batch_id)
    rows = (
        db.query(BatchStatusHistory)
        .filter(BatchStatusHistory.batch_id == batch_id)
        .order_by(BatchStatusHistory.changed_at)
        .all()
    )
    return [BatchHistoryItem.model_validate(r) for r in rows]
