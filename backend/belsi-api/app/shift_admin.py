"""
Curator/admin shift management — reopen, edit time, edit object, with audit log.
Mounted under /curator router (existing).
"""
from datetime import datetime, timezone
from typing import Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy import text as sa_text
from sqlalchemy.orm import Session

from .auth import get_current_user
from .db import get_db
from .models import Shift, User

# Этот router монтируется в curator.py через include_router OR
# просто добавляются функции в существующий /curator router.
admin_router = APIRouter(prefix="/curator", tags=["curator-shift-admin"])


def _require_curator(user: User):
    if (user.role or "").lower() not in ("curator", "coordinator"):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Только куратор/координатор может редактировать смены",
        )


def _shift_snapshot(shift: Shift) -> dict:
    """Собрать снимок смены для audit log."""
    return {
        "status": shift.status,
        "start_at": shift.start_at.isoformat() if shift.start_at else None,
        "finish_at": shift.finish_at.isoformat() if shift.finish_at else None,
        "site_object_id": str(shift.site_object_id) if shift.site_object_id else None,
        "total_seconds": int(shift.total_seconds or 0),
        "pause_seconds": int(shift.pause_seconds or 0),
        "idle_seconds": int(shift.idle_seconds or 0),
    }


def _audit(
    db: Session,
    shift_id: UUID,
    action: str,
    old: dict,
    new: dict,
    user: User,
    reason: Optional[str],
):
    db.execute(
        sa_text(
            """
            INSERT INTO shift_audit_log
                (shift_id, action, performed_by, performed_by_name, performed_by_role, old_values, new_values, reason)
            VALUES
                (:sid, :action, :uid, :uname, :urole, CAST(:oldv AS jsonb), CAST(:newv AS jsonb), :reason)
            """
        ),
        {
            "sid": str(shift_id),
            "action": action,
            "uid": str(user.id),
            "uname": " ".join(filter(None, [user.first_name, user.last_name])).strip()
                    or user.full_name or user.phone,
            "urole": user.role or "",
            "oldv": __import__("json").dumps(old, ensure_ascii=False),
            "newv": __import__("json").dumps(new, ensure_ascii=False),
            "reason": reason,
        },
    )


# ─── Schemas ─────────────────────────────────────────────────────────

class ReopenShiftRequest(BaseModel):
    reason: Optional[str] = None


class EditShiftRequest(BaseModel):
    start_at: Optional[datetime] = None
    finish_at: Optional[datetime] = None
    site_object_id: Optional[UUID] = None
    reason: Optional[str] = None


class AuditEntryOut(BaseModel):
    id: str
    action: str
    performed_by_name: Optional[str]
    performed_by_role: Optional[str]
    old_values: Optional[dict]
    new_values: Optional[dict]
    reason: Optional[str]
    created_at: str


# ─── Endpoints ───────────────────────────────────────────────────────

@admin_router.post("/shifts/{shift_id}/reopen")
def reopen_shift(
    shift_id: UUID,
    payload: ReopenShiftRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Re-open закрытой смены. Status=active, finish_at=NULL.
    Триггер sync_shifts_timestamps теперь корректно обнуляет finished_at."""
    _require_curator(current_user)

    shift = db.query(Shift).filter(Shift.id == shift_id).first()
    if not shift:
        raise HTTPException(status_code=404, detail="Смена не найдена")
    if shift.status == "active":
        raise HTTPException(status_code=400, detail="Смена и так активна")

    old = _shift_snapshot(shift)

    shift.status = "active"
    shift.finish_at = None
    shift.duration_hours = None
    db.add(shift)
    db.flush()

    new = _shift_snapshot(shift)
    _audit(db, shift.id, "reopen", old, new, current_user, payload.reason)
    db.commit()

    return {
        "status": "ok",
        "shift_id": str(shift.id),
        "new_state": new,
    }


@admin_router.patch("/shifts/{shift_id}")
def edit_shift(
    shift_id: UUID,
    payload: EditShiftRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Редактирование времени/объекта смены.
    Все изменения логируются в shift_audit_log."""
    _require_curator(current_user)

    shift = db.query(Shift).filter(Shift.id == shift_id).first()
    if not shift:
        raise HTTPException(status_code=404, detail="Смена не найдена")

    old = _shift_snapshot(shift)
    changed = False

    if payload.start_at is not None:
        shift.start_at = payload.start_at
        changed = True

    if payload.finish_at is not None:
        # Если явно передали finish_at — это закрытие или коррекция.
        # Если хотим re-open — используй /reopen endpoint.
        shift.finish_at = payload.finish_at
        changed = True

    if payload.site_object_id is not None:
        shift.site_object_id = payload.site_object_id
        changed = True

    if not changed:
        raise HTTPException(status_code=400, detail="Не указано ни одно поле для изменения")

    # Sanity-check: start_at < finish_at
    if shift.start_at and shift.finish_at and shift.finish_at <= shift.start_at:
        raise HTTPException(
            status_code=400,
            detail="finish_at должно быть позже start_at",
        )

    db.add(shift)
    db.flush()

    # Пересчёт total_seconds на основании новых меток (если обе заданы)
    if shift.start_at and shift.finish_at:
        wall = int((shift.finish_at - shift.start_at).total_seconds())
        pause = int(shift.pause_seconds or 0)
        idle = int(shift.idle_seconds or 0)
        shift.total_seconds = max(0, wall - pause - idle)
        db.add(shift)
        db.flush()

    new = _shift_snapshot(shift)

    # Action: edit_time | edit_object | edit_both
    if old["site_object_id"] != new["site_object_id"]:
        action = "edit_object" if old["start_at"] == new["start_at"] and old["finish_at"] == new["finish_at"] else "edit_both"
    else:
        action = "edit_time"

    _audit(db, shift.id, action, old, new, current_user, payload.reason)
    db.commit()

    return {
        "status": "ok",
        "shift_id": str(shift.id),
        "new_state": new,
    }


@admin_router.get("/shifts/{shift_id}/audit")
def get_shift_audit(
    shift_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """История изменений смены (для куратора/координатора)."""
    _require_curator(current_user)

    rows = db.execute(
        sa_text(
            """
            SELECT id, action, performed_by_name, performed_by_role,
                   old_values, new_values, reason, created_at
            FROM shift_audit_log
            WHERE shift_id = :sid
            ORDER BY created_at DESC
            LIMIT 200
            """
        ),
        {"sid": str(shift_id)},
    ).mappings().all()

    return {
        "shift_id": str(shift_id),
        "entries": [
            {
                "id": str(r["id"]),
                "action": r["action"],
                "performed_by_name": r["performed_by_name"],
                "performed_by_role": r["performed_by_role"],
                "old_values": r["old_values"],
                "new_values": r["new_values"],
                "reason": r["reason"],
                "created_at": r["created_at"].isoformat() if r["created_at"] else None,
            }
            for r in rows
        ],
    }


@admin_router.get("/users/{user_id}/shifts")
def list_user_shifts(
    user_id: UUID,
    limit: int = 50,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список последних смен пользователя — для редактирования из UI куратора."""
    _require_curator(current_user)

    rows = db.execute(
        sa_text(
            """
            SELECT s.id, s.status,
                   s.start_at, s.finish_at,
                   s.total_seconds, s.pause_seconds, s.idle_seconds,
                   s.site_object_id,
                   so.name AS object_name,
                   (SELECT COUNT(*) FROM shift_audit_log WHERE shift_id = s.id) AS audit_count
            FROM shifts s
            LEFT JOIN site_objects so ON so.id = s.site_object_id
            WHERE s.user_id = :uid
            ORDER BY s.start_at DESC NULLS LAST
            LIMIT :limit
            """
        ),
        {"uid": str(user_id), "limit": limit},
    ).mappings().all()

    return {
        "user_id": str(user_id),
        "shifts": [
            {
                "id": str(r["id"]),
                "status": r["status"],
                "start_at": r["start_at"].isoformat() if r["start_at"] else None,
                "finish_at": r["finish_at"].isoformat() if r["finish_at"] else None,
                "total_seconds": int(r["total_seconds"] or 0),
                "pause_seconds": int(r["pause_seconds"] or 0),
                "idle_seconds": int(r["idle_seconds"] or 0),
                "site_object_id": str(r["site_object_id"]) if r["site_object_id"] else None,
                "object_name": r["object_name"],
                "audit_count": int(r["audit_count"] or 0),
            }
            for r in rows
        ],
    }
