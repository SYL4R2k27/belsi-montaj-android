"""
BELSI 2.0.0 build3 · BRAND-CORE: мульти-роль, timeline объекта, pipeline партии.

Реализует 4 ключевых концепта из брендбука:
- Multi-role (capabilities) до 3 на юзера + RoleSwitcher
- Object timeline — единая лента всех событий объекта
- Pipeline партии — связки batch ↔ delivery ↔ route
- Idle reasons по доменам

FIX(2026-05-11) BELSI 2.0.0 build3.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime, date, timezone
from typing import List, Optional, Dict, Any
from uuid import UUID
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User


# ═══════════════════════════════════════════════════════════════════════════
# 1. MULTI-ROLE — /user/me/roles + curator-управление
# ═══════════════════════════════════════════════════════════════════════════

user_role_router = APIRouter(prefix="/user", tags=["multi-role"])
admin_role_router = APIRouter(prefix="/users", tags=["multi-role-admin"])


class RoleAssignmentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    role: str
    facility_id: Optional[UUID] = None
    facility_name: Optional[str] = None
    is_active: bool
    is_primary: bool
    granted_by: Optional[UUID] = None
    granted_at: datetime


class RoleGrantIn(BaseModel):
    role: str = Field(pattern=r"^(installer|foreman|coordinator|curator|driver|logistician|production_chief|senior_worker|worker|supplier|engineer)$")
    facility_id: Optional[UUID] = None
    is_primary: bool = False


@user_role_router.get("/me/roles", response_model=List[RoleAssignmentOut])
def my_roles(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список моих активных ролей. Используется клиентом при логине для RoleSelectBottomSheet."""
    rows = db.execute(text("""
        SELECT ra.id, ra.role, ra.facility_id, so.name AS facility_name,
               ra.is_active, ra.is_primary, ra.granted_by, ra.granted_at
        FROM user_role_assignments ra
        LEFT JOIN site_objects so ON so.id = ra.facility_id
        WHERE ra.user_id = :uid AND ra.is_active = TRUE
        ORDER BY ra.is_primary DESC, ra.granted_at
    """), {"uid": current_user.id}).mappings().all()
    return [dict(r) for r in rows]


@admin_role_router.get("/{user_id}/roles", response_model=List[RoleAssignmentOut])
def list_user_roles(
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Куратор-only: посмотреть все роли пользователя."""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор")
    rows = db.execute(text("""
        SELECT ra.id, ra.role, ra.facility_id, so.name AS facility_name,
               ra.is_active, ra.is_primary, ra.granted_by, ra.granted_at
        FROM user_role_assignments ra
        LEFT JOIN site_objects so ON so.id = ra.facility_id
        WHERE ra.user_id = :uid
        ORDER BY ra.is_active DESC, ra.is_primary DESC, ra.granted_at
    """), {"uid": user_id}).mappings().all()
    return [dict(r) for r in rows]


@admin_role_router.post("/{user_id}/roles/grant", response_model=RoleAssignmentOut, status_code=201)
def grant_role(
    user_id: UUID,
    payload: RoleGrantIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Куратор-only: выдать роль. Триггер БД проверит лимит 3."""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор может выдавать роли")
    # Проверка что user_id существует
    exists = db.execute(text("SELECT 1 FROM users WHERE id = :uid"), {"uid": user_id}).first()
    if not exists:
        raise HTTPException(status_code=404, detail="Пользователь не найден")
    try:
        row = db.execute(text("""
            INSERT INTO user_role_assignments
              (user_id, role, facility_id, is_active, is_primary, granted_by)
            VALUES (:uid, :role, :fid, TRUE, :primary, :gb)
            RETURNING *
        """), {
            "uid": user_id, "role": payload.role, "fid": payload.facility_id,
            "primary": payload.is_primary, "gb": current_user.id
        }).mappings().first()
        # role_change_log audit
        db.execute(text("""
            INSERT INTO role_change_log (user_id, from_role, to_role, changed_by, reason)
            VALUES (:uid, :fr, :tr, :cb, :r)
        """), {"uid": user_id, "fr": "n/a", "tr": payload.role, "cb": current_user.id,
               "r": f"grant via curator {current_user.id}"})
        db.commit()
    except Exception as e:
        db.rollback()
        if "already has 3 active roles" in str(e):
            raise HTTPException(status_code=400, detail="Лимит 3 активные роли превышен. Отзови одну.")
        raise HTTPException(status_code=400, detail=str(e))
    return dict(row)


@admin_role_router.post("/{user_id}/roles/{role_id}/revoke", response_model=RoleAssignmentOut)
def revoke_role(
    user_id: UUID,
    role_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Куратор-only: отозвать роль (мягкое удаление — is_active=FALSE)."""
    if current_user.role != "curator":
        raise HTTPException(status_code=403, detail="Только куратор")
    row = db.execute(text("""
        UPDATE user_role_assignments
        SET is_active = FALSE, revoked_at = NOW()
        WHERE id = :rid AND user_id = :uid
        RETURNING *
    """), {"rid": role_id, "uid": user_id}).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Роль не найдена")
    db.commit()
    return dict(row)


@user_role_router.post("/me/active-role", response_model=Dict[str, str])
def set_active_role(
    payload: Dict[str, str],
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Юзер выбирает активную роль (для RoleSwitcher в шапке).
    Backend синхронизирует users.role для backward compat со старыми endpoint'ами."""
    role = (payload.get("role") or "").strip().lower()
    if not role:
        raise HTTPException(status_code=400, detail="role required")
    # Проверка что у юзера есть эта роль активная
    has = db.execute(text("""
        SELECT 1 FROM user_role_assignments
        WHERE user_id = :uid AND role = :r AND is_active = TRUE LIMIT 1
    """), {"uid": current_user.id, "r": role}).first()
    if not has:
        raise HTTPException(status_code=403, detail=f"У вас нет активной роли '{role}'")
    db.execute(text("UPDATE users SET role = :r WHERE id = :uid"), {"r": role, "uid": current_user.id})
    db.commit()
    return {"active_role": role}


# ═══════════════════════════════════════════════════════════════════════════
# 2. OBJECT TIMELINE — единая лента событий объекта
# ═══════════════════════════════════════════════════════════════════════════

timeline_router = APIRouter(prefix="/objects", tags=["object-timeline"])


class TimelineEventOut(BaseModel):
    type: str  # shift_start/shift_end/photo/audit/delivery/batch/task/ticket
    domain: str  # installation | logistics | production | curator
    occurred_at: datetime
    actor_id: Optional[UUID] = None
    actor_name: Optional[str] = None
    icon: str
    title: str
    detail: Optional[str] = None
    payload: Dict[str, Any] = {}


@timeline_router.get("/{object_id}/timeline", response_model=List[TimelineEventOut])
def object_timeline(
    object_id: UUID,
    date_from: Optional[date] = Query(None),
    types: Optional[str] = Query(None, description="comma-sep: shift,photo,delivery,batch,audit,task,ticket"),
    limit: int = Query(200, ge=1, le=500),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Единая лента событий объекта.
    Брендбук раздел 03: «история объекта от заказа до сдачи в одном экране».

    Source tables (UNION ALL):
      - shifts (старт/конец смены монтажников)
      - shift_photos (фотоотчёты)
      - shift_audit_log (правки куратора)
      - delivery_requests (доставки)
      - driver_route_points (приезды водителей)
      - production_batches (партии на этот объект)
      - tasks
      - support_tickets
    """
    type_filter = set((types or "shift,photo,delivery,batch,audit,task,ticket").split(","))
    df = date_from or (datetime.now(timezone.utc).date().replace(day=1))  # с начала месяца по умолчанию
    events: List[Dict[str, Any]] = []

    # 1. Shifts
    if "shift" in type_filter:
        rows = db.execute(text("""
            SELECT s.id, s.user_id, COALESCE(u.full_name, u.phone) AS actor_name,
                   s.start_at, s.end_at
            FROM shifts s LEFT JOIN users u ON u.id = s.user_id
            WHERE s.site_object_id = :oid AND s.start_at >= :df
            ORDER BY s.start_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all()
        for r in rows:
            events.append({
                "type": "shift_start", "domain": "installation", "occurred_at": r["start_at"],
                "actor_id": r["user_id"], "actor_name": r["actor_name"],
                "icon": "🔨", "title": f"{r['actor_name']} открыл смену",
                "detail": None, "payload": {"shift_id": str(r["id"])},
            })
            if r["end_at"]:
                duration = (r["end_at"] - r["start_at"]).total_seconds()
                hrs, mins = int(duration // 3600), int((duration % 3600) // 60)
                events.append({
                    "type": "shift_end", "domain": "installation", "occurred_at": r["end_at"],
                    "actor_id": r["user_id"], "actor_name": r["actor_name"],
                    "icon": "✅", "title": f"{r['actor_name']} завершил смену",
                    "detail": f"{hrs} ч {mins} мин", "payload": {"shift_id": str(r["id"])},
                })

    # 2. Photos
    if "photo" in type_filter:
        rows = db.execute(text("""
            SELECT sp.id, sp.created_at, sp.user_id, sp.hour_label, sp.ai_score, sp.ai_comment,
                   COALESCE(u.full_name, u.phone) AS actor_name
            FROM shift_photos sp
            LEFT JOIN shifts s ON s.id = sp.shift_id
            LEFT JOIN users u ON u.id = sp.user_id
            WHERE s.site_object_id = :oid AND sp.created_at >= :df
            ORDER BY sp.created_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all()
        for r in rows:
            ai_part = ""
            if r["ai_score"] is not None:
                ai_part = f" · AI {r['ai_score']}/100"
            events.append({
                "type": "photo", "domain": "installation", "occurred_at": r["created_at"],
                "actor_id": r["user_id"], "actor_name": r["actor_name"],
                "icon": "📸", "title": f"{r['actor_name']}: фото '{r['hour_label'] or '—'}'",
                "detail": (r["ai_comment"] or "") + ai_part,
                "payload": {"photo_id": str(r["id"]), "ai_score": r["ai_score"]},
            })

    # 3. Shift audit
    if "audit" in type_filter:
        rows = db.execute(text("""
            SELECT sal.id, sal.created_at, sal.changed_by, sal.action_type, sal.reason,
                   COALESCE(u.full_name, u.phone) AS actor_name
            FROM shift_audit_log sal
            LEFT JOIN shifts s ON s.id = sal.shift_id
            LEFT JOIN users u ON u.id = sal.changed_by
            WHERE s.site_object_id = :oid AND sal.created_at >= :df
            ORDER BY sal.created_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all()
        for r in rows:
            events.append({
                "type": "audit", "domain": "curator", "occurred_at": r["created_at"],
                "actor_id": r["changed_by"], "actor_name": r["actor_name"],
                "icon": "⚙️", "title": f"Куратор: {r['action_type']}",
                "detail": r["reason"], "payload": {"shift_audit_id": str(r["id"])},
            })

    # 4. Deliveries
    if "delivery" in type_filter:
        rows = db.execute(text("""
            SELECT dr.id, dr.created_at, dr.cargo, dr.status, dr.priority,
                   dr.created_by, COALESCE(u.full_name, u.phone) AS actor_name
            FROM delivery_requests dr
            LEFT JOIN users u ON u.id = dr.created_by
            WHERE dr.site_object_id = :oid AND dr.created_at >= :df
            ORDER BY dr.created_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all()
        for r in rows:
            icon = "🚛" if r["status"] in ("delivered", "in_transit") else "📋"
            events.append({
                "type": "delivery", "domain": "logistics", "occurred_at": r["created_at"],
                "actor_id": r["created_by"], "actor_name": r["actor_name"],
                "icon": icon, "title": f"Заявка: {r['cargo'][:60]}",
                "detail": f"Статус: {r['status']}, приоритет: {r['priority']}",
                "payload": {"request_id": str(r["id"]), "status": r["status"]},
            })

    # 5. Route points DELIVERED at this object
    if "delivery" in type_filter:
        rows = db.execute(text("""
            SELECT rp.id, rp.delivered_at, rp.cargo, rp.address,
                   r.driver_id, COALESCE(u.full_name, u.phone) AS driver_name
            FROM driver_route_points rp
            LEFT JOIN driver_routes r ON r.id = rp.route_id
            LEFT JOIN users u ON u.id = r.driver_id
            WHERE rp.site_object_id = :oid AND rp.delivered_at IS NOT NULL
              AND rp.delivered_at >= :df
            ORDER BY rp.delivered_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all()
        for r in rows:
            events.append({
                "type": "delivery_complete", "domain": "logistics", "occurred_at": r["delivered_at"],
                "actor_id": r["driver_id"], "actor_name": r["driver_name"],
                "icon": "📍", "title": f"{r['driver_name']} привёз груз",
                "detail": r["cargo"], "payload": {"point_id": str(r["id"])},
            })

    # 6. Batches (target_object_id)
    if "batch" in type_filter:
        rows = db.execute(text("""
            SELECT pb.id, pb.created_at, pb.cargo_description, pb.status, pb.deadline,
                   pb.created_by, COALESCE(u.full_name, u.phone) AS actor_name
            FROM production_batches pb
            LEFT JOIN users u ON u.id = pb.created_by
            WHERE pb.target_object_id = :oid AND pb.created_at >= :df
            ORDER BY pb.created_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all()
        for r in rows:
            events.append({
                "type": "batch", "domain": "production", "occurred_at": r["created_at"],
                "actor_id": r["created_by"], "actor_name": r["actor_name"],
                "icon": "📦", "title": f"Партия создана: {(r['cargo_description'] or '')[:60]}",
                "detail": f"Статус: {r['status']}, дедлайн: {r['deadline']}",
                "payload": {"batch_id": str(r["id"]), "status": r["status"]},
            })

    # 7. Tasks
    if "task" in type_filter:
        rows = db.execute(text("""
            SELECT t.id, t.created_at, t.title, t.status,
                   t.assigned_to, COALESCE(u.full_name, u.phone) AS actor_name
            FROM tasks t LEFT JOIN users u ON u.id = t.assigned_to
            WHERE t.site_object_id = :oid AND t.created_at >= :df
            ORDER BY t.created_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all()
        for r in rows:
            events.append({
                "type": "task", "domain": "installation", "occurred_at": r["created_at"],
                "actor_id": r["assigned_to"], "actor_name": r["actor_name"],
                "icon": "✅", "title": f"Задача: {r['title'][:60]}",
                "detail": f"Статус: {r['status']}",
                "payload": {"task_id": str(r["id"])},
            })

    # 8. Tickets
    if "ticket" in type_filter:
        rows = db.execute(text("""
            SELECT st.id, st.created_at, st.subject, st.status, st.priority,
                   st.user_id, COALESCE(u.full_name, u.phone) AS actor_name
            FROM support_tickets st LEFT JOIN users u ON u.id = st.user_id
            WHERE st.site_object_id = :oid AND st.created_at >= :df
            ORDER BY st.created_at DESC LIMIT :lim
        """), {"oid": object_id, "df": df, "lim": limit}).mappings().all() if _has_col(db, "support_tickets", "site_object_id") else []
        for r in rows:
            events.append({
                "type": "ticket", "domain": "curator", "occurred_at": r["created_at"],
                "actor_id": r["user_id"], "actor_name": r["actor_name"],
                "icon": "🎫", "title": f"Тикет: {r['subject'][:60]}",
                "detail": f"Статус: {r['status']}, приоритет: {r['priority']}",
                "payload": {"ticket_id": str(r["id"])},
            })

    # Sort + limit
    events.sort(key=lambda e: e["occurred_at"], reverse=True)
    return events[:limit]


def _has_col(db: Session, table: str, col: str) -> bool:
    r = db.execute(text("""
        SELECT 1 FROM information_schema.columns
        WHERE table_name = :t AND column_name = :c LIMIT 1
    """), {"t": table, "c": col}).first()
    return r is not None


# ═══════════════════════════════════════════════════════════════════════════
# 3. IDLE REASONS — список по доменам
# ═══════════════════════════════════════════════════════════════════════════

idle_router = APIRouter(prefix="/idle-reasons", tags=["idle-reasons"])


class IdleReasonOut(BaseModel):
    code: str
    label_ru: str
    sort_order: int


@idle_router.get("", response_model=List[IdleReasonOut])
def list_idle_reasons(
    domain: str = Query(..., pattern=r"^(production|installation|logistics)$"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Причины простоя по домену. Брендбук раздел 13."""
    rows = db.execute(text("""
        SELECT code, label_ru, sort_order FROM idle_reasons_catalog
        WHERE domain = :d AND is_active = TRUE
        ORDER BY sort_order, label_ru
    """), {"d": domain}).mappings().all()
    return [dict(r) for r in rows]


# ═══════════════════════════════════════════════════════════════════════════
# 4. PIPELINE batch timeline — где партия физически прямо сейчас
# ═══════════════════════════════════════════════════════════════════════════

pipeline_router = APIRouter(prefix="/production/batches", tags=["pipeline"])


class BatchTimelineEvent(BaseModel):
    occurred_at: datetime
    stage: str  # draft/in_production/ready_to_ship/in_route/delivered/installed
    actor_id: Optional[UUID] = None
    actor_name: Optional[str] = None
    icon: str
    title: str
    detail: Optional[str] = None


@pipeline_router.get("/{batch_id}/pipeline", response_model=List[BatchTimelineEvent])
def batch_pipeline(
    batch_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Полный путь партии: создание → монтаж. Использует batch_status_history + связанные route_points."""
    events: List[Dict[str, Any]] = []

    # Statuses из batch_status_history
    rows = db.execute(text("""
        SELECT bsh.created_at, bsh.from_status, bsh.to_status, bsh.reason,
               bsh.changed_by, COALESCE(u.full_name, u.phone) AS actor_name
        FROM batch_status_history bsh
        LEFT JOIN users u ON u.id = bsh.changed_by
        WHERE bsh.batch_id = :bid
        ORDER BY bsh.created_at
    """), {"bid": batch_id}).mappings().all()
    icon_map = {
        "draft": "📝", "in_production": "🔨", "ready_to_ship": "📦",
        "in_route": "🚛", "delivered": "📍", "installed": "✅",
    }
    for r in rows:
        events.append({
            "occurred_at": r["created_at"],
            "stage": r["to_status"],
            "actor_id": r["changed_by"],
            "actor_name": r["actor_name"],
            "icon": icon_map.get(r["to_status"], "•"),
            "title": f"{r['from_status'] or '—'} → {r['to_status']}",
            "detail": r["reason"],
        })

    # Route points связанные с партией
    rp = db.execute(text("""
        SELECT rp.id, rp.delivered_at, rp.arrived_at, rp.address, rp.cargo,
               r.driver_id, COALESCE(u.full_name, u.phone) AS driver_name
        FROM driver_route_points rp
        LEFT JOIN driver_routes r ON r.id = rp.route_id
        LEFT JOIN users u ON u.id = r.driver_id
        WHERE rp.batch_id = :bid
        ORDER BY COALESCE(rp.delivered_at, rp.arrived_at)
    """), {"bid": batch_id}).mappings().all()
    for r in rp:
        if r["arrived_at"]:
            events.append({
                "occurred_at": r["arrived_at"], "stage": "in_route",
                "actor_id": r["driver_id"], "actor_name": r["driver_name"],
                "icon": "🚛", "title": f"Водитель прибыл на {r['address']}",
                "detail": r["cargo"],
            })
        if r["delivered_at"]:
            events.append({
                "occurred_at": r["delivered_at"], "stage": "delivered",
                "actor_id": r["driver_id"], "actor_name": r["driver_name"],
                "icon": "📍", "title": f"Доставлено: {r['address']}",
                "detail": r["cargo"],
            })

    events.sort(key=lambda e: e["occurred_at"])
    return events
