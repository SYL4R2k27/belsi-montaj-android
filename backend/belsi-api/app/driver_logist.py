"""
Driver / Logistician domain — маршруты, точки, заявки на доставку.

FIX(2026-05-11) BELSI 2.0.0: реализация под mock-UI клиента, который раньше
работал на DriverMockData. Теперь все потоки идут через реальный backend.

Архитектура:
- Logistician создаёт driver_route + driver_route_points, назначает driver_id.
- Driver видит свой маршрут на сегодня, отмечает прибытие и доставку.
- Координатор/куратор может создать delivery_request → логист её матчит к маршруту.
"""
from __future__ import annotations

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from datetime import datetime, date, timezone
from typing import List, Optional
from uuid import UUID
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User


# ═══════════════════════════════════════════════════════════════════════════
# Schemas (Pydantic)
# ═══════════════════════════════════════════════════════════════════════════

class RoutePointIn(BaseModel):
    seq: int
    point_type: str = Field(pattern=r"^(pickup|delivery|transit|return)$")
    address: str
    scheduled_time: Optional[str] = None
    site_object_id: Optional[UUID] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    cargo: Optional[str] = None
    notes: Optional[str] = None


class RoutePointOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    seq: int
    point_type: str
    address: str
    scheduled_time: Optional[str] = None
    site_object_id: Optional[UUID] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    cargo: Optional[str] = None
    status: str
    arrived_at: Optional[datetime] = None
    delivered_at: Optional[datetime] = None
    photo_url: Optional[str] = None
    skip_reason: Optional[str] = None
    notes: Optional[str] = None


class RouteCreateIn(BaseModel):
    driver_id: UUID
    planned_date: date
    notes: Optional[str] = None
    points: List[RoutePointIn]


class RouteOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    driver_id: UUID
    driver_name: Optional[str] = None
    logistician_id: Optional[UUID] = None
    logistician_name: Optional[str] = None
    planned_date: date
    status: str
    started_at: Optional[datetime] = None
    completed_at: Optional[datetime] = None
    total_points: int
    completed_points: int
    notes: Optional[str] = None
    points: List[RoutePointOut] = []


class DeliveryRequestIn(BaseModel):
    site_object_id: Optional[UUID] = None
    object_name_snapshot: Optional[str] = None
    cargo: str
    need_by_time: Optional[str] = None
    need_by_date: Optional[date] = None
    priority: str = Field(default="normal", pattern=r"^(low|normal|high|urgent)$")
    notes: Optional[str] = None


class DeliveryRequestOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    created_by: UUID
    creator_name: Optional[str] = None
    site_object_id: Optional[UUID] = None
    object_name_snapshot: Optional[str] = None
    cargo: str
    need_by_time: Optional[str] = None
    need_by_date: date
    priority: str
    status: str
    assigned_route_id: Optional[UUID] = None
    notes: Optional[str] = None
    created_at: datetime


class DriverFleetItemOut(BaseModel):
    id: UUID
    name: Optional[str] = None
    phone: str
    status: str  # active | free | offline
    current_route_id: Optional[UUID] = None
    current_progress: Optional[str] = None  # "1/4"


class SkipPointIn(BaseModel):
    reason: str


class AssignRequestIn(BaseModel):
    route_id: UUID
    point_id: Optional[UUID] = None


# ═══════════════════════════════════════════════════════════════════════════
# Routers
# ═══════════════════════════════════════════════════════════════════════════

logist_router = APIRouter(prefix="/logistician", tags=["logistician"])
driver_router = APIRouter(prefix="/driver", tags=["driver"])


# ═══════════════════════════════════════════════════════════════════════════
# Helpers
# ═══════════════════════════════════════════════════════════════════════════

def _require_logist(user: User) -> None:
    role = (user.role or "").strip().lower()
    # Куратор и координатор тоже могут видеть и создавать маршруты (управленческий контроль)
    if role not in ("logistician", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Только логист/куратор/координатор")


def _require_driver(user: User) -> None:
    role = (user.role or "").strip().lower()
    if role not in ("driver", "curator"):  # куратор для теста role-switcher
        raise HTTPException(status_code=403, detail="Только водитель")


def _route_to_out(db: Session, row: dict, with_points: bool = True) -> dict:
    """Преобразует строку driver_routes (как dict) в формат RouteOut с .points."""
    rid = row["id"]
    # driver name
    drv = db.execute(text("SELECT full_name, phone FROM users WHERE id = :uid"), {"uid": row["driver_id"]}).first()
    driver_name = (drv[0] or drv[1]) if drv else None
    # logist name
    logist_name = None
    if row.get("logistician_id"):
        lg = db.execute(text("SELECT full_name, phone FROM users WHERE id = :uid"), {"uid": row["logistician_id"]}).first()
        logist_name = (lg[0] or lg[1]) if lg else None

    out = {
        "id": rid,
        "driver_id": row["driver_id"],
        "driver_name": driver_name,
        "logistician_id": row.get("logistician_id"),
        "logistician_name": logist_name,
        "planned_date": row["planned_date"],
        "status": row["status"],
        "started_at": row.get("started_at"),
        "completed_at": row.get("completed_at"),
        "total_points": row["total_points"],
        "completed_points": row["completed_points"],
        "notes": row.get("notes"),
        "points": [],
    }
    if with_points:
        pts = db.execute(
            text("""SELECT id, seq, point_type, address, scheduled_time,
                          site_object_id, latitude, longitude, cargo, status,
                          arrived_at, delivered_at, photo_url, skip_reason, notes
                   FROM driver_route_points WHERE route_id = :rid ORDER BY seq"""),
            {"rid": rid}
        ).mappings().all()
        out["points"] = [dict(p) for p in pts]
    return out


def _refresh_route_counters(db: Session, route_id: UUID) -> None:
    """Пересчитывает total_points/completed_points и status маршрута."""
    db.execute(text("""
        UPDATE driver_routes SET
            total_points = (SELECT COUNT(*) FROM driver_route_points WHERE route_id = :rid),
            completed_points = (SELECT COUNT(*) FROM driver_route_points
                               WHERE route_id = :rid AND status IN ('delivered', 'skipped')),
            updated_at = NOW()
        WHERE id = :rid
    """), {"rid": route_id})
    # Auto-complete if all points done
    row = db.execute(
        text("SELECT total_points, completed_points, status FROM driver_routes WHERE id = :rid"),
        {"rid": route_id}
    ).first()
    if row and row[0] > 0 and row[0] == row[1] and row[2] == "active":
        db.execute(
            text("UPDATE driver_routes SET status = 'completed', completed_at = NOW() WHERE id = :rid"),
            {"rid": route_id}
        )


# ═══════════════════════════════════════════════════════════════════════════
# LOGISTICIAN endpoints
# ═══════════════════════════════════════════════════════════════════════════

@logist_router.get("/routes", response_model=List[RouteOut])
def list_routes(
    date_from: Optional[date] = None,
    status: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список маршрутов на дату/диапазон. По умолчанию — сегодня."""
    _require_logist(current_user)
    today = date_from or date.today()
    q = "SELECT * FROM driver_routes WHERE planned_date >= :d"
    params = {"d": today}
    if status:
        q += " AND status = :s"
        params["s"] = status
    q += " ORDER BY planned_date DESC, created_at DESC"
    rows = db.execute(text(q), params).mappings().all()
    return [_route_to_out(db, dict(r)) for r in rows]


@logist_router.post("/routes", response_model=RouteOut, status_code=201)
def create_route(
    payload: RouteCreateIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Создать маршрут с N точками, назначить водителю."""
    _require_logist(current_user)

    # Verify driver exists with role driver
    drv = db.execute(
        text("SELECT id, role FROM users WHERE id = :uid"),
        {"uid": payload.driver_id}
    ).first()
    if not drv:
        raise HTTPException(status_code=404, detail="Водитель не найден")
    if drv[1] not in ("driver", "curator"):
        raise HTTPException(status_code=400, detail=f"Пользователь имеет роль '{drv[1]}', не 'driver'")

    # Insert route
    row = db.execute(text("""
        INSERT INTO driver_routes (driver_id, logistician_id, planned_date, status, total_points, notes)
        VALUES (:did, :lid, :pd, 'planned', :tp, :n)
        RETURNING id
    """), {
        "did": payload.driver_id,
        "lid": current_user.id,
        "pd": payload.planned_date,
        "tp": len(payload.points),
        "n": payload.notes,
    }).first()
    route_id = row[0]

    # Insert points
    for p in payload.points:
        db.execute(text("""
            INSERT INTO driver_route_points
              (route_id, seq, point_type, address, scheduled_time, site_object_id,
               latitude, longitude, cargo, notes)
            VALUES
              (:rid, :seq, :pt, :addr, :st, :soid, :lat, :lng, :cargo, :n)
        """), {
            "rid": route_id, "seq": p.seq, "pt": p.point_type, "addr": p.address,
            "st": p.scheduled_time, "soid": p.site_object_id,
            "lat": p.latitude, "lng": p.longitude,
            "cargo": p.cargo, "n": p.notes,
        })
    db.commit()

    res = db.execute(text("SELECT * FROM driver_routes WHERE id = :rid"), {"rid": route_id}).mappings().first()
    return _route_to_out(db, dict(res))


@logist_router.get("/routes/{route_id}", response_model=RouteOut)
def get_route(
    route_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _require_logist(current_user)
    row = db.execute(text("SELECT * FROM driver_routes WHERE id = :rid"), {"rid": route_id}).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Маршрут не найден")
    return _route_to_out(db, dict(row))


@logist_router.delete("/routes/{route_id}", status_code=204)
def cancel_route(
    route_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Отменить маршрут (status → cancelled)."""
    _require_logist(current_user)
    row = db.execute(text("SELECT status FROM driver_routes WHERE id = :rid"), {"rid": route_id}).first()
    if not row:
        raise HTTPException(status_code=404, detail="Маршрут не найден")
    if row[0] in ("completed", "cancelled"):
        raise HTTPException(status_code=400, detail=f"Маршрут уже в статусе '{row[0]}'")
    db.execute(text("UPDATE driver_routes SET status = 'cancelled', updated_at = NOW() WHERE id = :rid"), {"rid": route_id})
    db.commit()


@logist_router.get("/requests", response_model=List[DeliveryRequestOut])
def list_requests(
    status: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список заявок на доставку."""
    _require_logist(current_user)
    q = "SELECT r.*, u.full_name AS creator_full_name FROM delivery_requests r LEFT JOIN users u ON u.id = r.created_by WHERE 1=1"
    params: dict = {}
    if status:
        q += " AND r.status = :s"
        params["s"] = status
    else:
        q += " AND r.status IN ('pending', 'assigned')"
    q += " ORDER BY r.need_by_date, r.priority DESC, r.created_at"
    rows = db.execute(text(q), params).mappings().all()
    out = []
    for r in rows:
        d = dict(r)
        d["creator_name"] = d.pop("creator_full_name", None)
        out.append(d)
    return out


@logist_router.post("/requests", response_model=DeliveryRequestOut, status_code=201)
def create_request(
    payload: DeliveryRequestIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Создать заявку на доставку (обычно от координатора/куратора)."""
    role = (current_user.role or "").strip().lower()
    if role not in ("coordinator", "curator", "foreman", "logistician"):
        raise HTTPException(status_code=403, detail="Только координатор/куратор/бригадир/логист")

    # Если задан site_object_id — снимем имя для snapshot
    name_snapshot = payload.object_name_snapshot
    if payload.site_object_id and not name_snapshot:
        n = db.execute(text("SELECT name FROM site_objects WHERE id = :sid"), {"sid": payload.site_object_id}).first()
        name_snapshot = n[0] if n else None

    row = db.execute(text("""
        INSERT INTO delivery_requests
          (created_by, site_object_id, object_name_snapshot, cargo,
           need_by_time, need_by_date, priority, status, notes)
        VALUES
          (:uid, :soid, :ons, :cargo, :nbt, :nbd, :pr, 'pending', :n)
        RETURNING *
    """), {
        "uid": current_user.id, "soid": payload.site_object_id,
        "ons": name_snapshot, "cargo": payload.cargo,
        "nbt": payload.need_by_time, "nbd": payload.need_by_date or date.today(),
        "pr": payload.priority, "n": payload.notes,
    }).mappings().first()
    db.commit()
    d = dict(row)
    d["creator_name"] = current_user.full_name or current_user.phone
    return d


@logist_router.post("/requests/{request_id}/assign", response_model=DeliveryRequestOut)
def assign_request(
    request_id: UUID,
    payload: AssignRequestIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Привязать заявку к существующему маршруту/точке."""
    _require_logist(current_user)
    row = db.execute(text("SELECT * FROM delivery_requests WHERE id = :rid"), {"rid": request_id}).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Заявка не найдена")
    if row["status"] not in ("pending", "assigned"):
        raise HTTPException(status_code=400, detail=f"Нельзя переназначить — статус '{row['status']}'")

    # Verify route
    rt = db.execute(text("SELECT id FROM driver_routes WHERE id = :rid"), {"rid": payload.route_id}).first()
    if not rt:
        raise HTTPException(status_code=404, detail="Маршрут не найден")

    db.execute(text("""
        UPDATE delivery_requests SET
            status = 'assigned',
            assigned_route_id = :route, assigned_point_id = :point,
            assigned_at = NOW(), updated_at = NOW()
        WHERE id = :rid
    """), {"route": payload.route_id, "point": payload.point_id, "rid": request_id})
    db.commit()
    out = db.execute(text("SELECT * FROM delivery_requests WHERE id = :rid"), {"rid": request_id}).mappings().first()
    d = dict(out)
    creator = db.execute(text("SELECT full_name FROM users WHERE id = :uid"), {"uid": d["created_by"]}).first()
    d["creator_name"] = creator[0] if creator else None
    return d


@logist_router.get("/drivers", response_model=List[DriverFleetItemOut])
def list_drivers(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Парк водителей со статусом."""
    _require_logist(current_user)
    rows = db.execute(text("""
        SELECT
          u.id, u.full_name, u.phone,
          (SELECT r.id FROM driver_routes r
             WHERE r.driver_id = u.id AND r.status IN ('planned', 'active')
             ORDER BY r.planned_date DESC LIMIT 1) AS current_route_id,
          (SELECT r.completed_points || '/' || r.total_points FROM driver_routes r
             WHERE r.driver_id = u.id AND r.status = 'active'
             ORDER BY r.started_at DESC NULLS LAST LIMIT 1) AS progress
        FROM users u
        WHERE u.role = 'driver'
        ORDER BY u.full_name NULLS LAST
    """)).mappings().all()
    out = []
    for r in rows:
        d = dict(r)
        status = "active" if d.get("current_route_id") else "free"
        out.append({
            "id": d["id"], "name": d.get("full_name"), "phone": d["phone"],
            "status": status, "current_route_id": d.get("current_route_id"),
            "current_progress": d.get("progress"),
        })
    return out


@logist_router.get("/dashboard")
def logist_dashboard(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Сводка по логистике на сегодня."""
    _require_logist(current_user)
    today = date.today()
    routes_today = db.execute(text("SELECT COUNT(*) FROM driver_routes WHERE planned_date = :d"), {"d": today}).scalar() or 0
    active_routes = db.execute(text("SELECT COUNT(*) FROM driver_routes WHERE status = 'active'")).scalar() or 0
    pending_reqs = db.execute(text("SELECT COUNT(*) FROM delivery_requests WHERE status = 'pending'")).scalar() or 0
    free_drivers = db.execute(text("""
        SELECT COUNT(*) FROM users u WHERE u.role = 'driver'
        AND NOT EXISTS (SELECT 1 FROM driver_routes r WHERE r.driver_id = u.id AND r.status IN ('planned', 'active'))
    """)).scalar() or 0
    return {
        "routes_today": routes_today,
        "active_routes": active_routes,
        "pending_requests": pending_reqs,
        "free_drivers": free_drivers,
    }


# ═══════════════════════════════════════════════════════════════════════════
# DRIVER endpoints
# ═══════════════════════════════════════════════════════════════════════════

@driver_router.get("/routes/today", response_model=List[RouteOut])
def driver_today(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Мои маршруты на сегодня (planned + active)."""
    _require_driver(current_user)
    rows = db.execute(text("""
        SELECT * FROM driver_routes
        WHERE driver_id = :uid
          AND planned_date = CURRENT_DATE
          AND status IN ('planned', 'active')
        ORDER BY status, planned_date DESC
    """), {"uid": current_user.id}).mappings().all()
    return [_route_to_out(db, dict(r)) for r in rows]


@driver_router.get("/routes/{route_id}", response_model=RouteOut)
def driver_route_detail(
    route_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _require_driver(current_user)
    row = db.execute(
        text("SELECT * FROM driver_routes WHERE id = :rid AND driver_id = :uid"),
        {"rid": route_id, "uid": current_user.id}
    ).mappings().first()
    if not row:
        raise HTTPException(status_code=404, detail="Маршрут не найден или не ваш")
    return _route_to_out(db, dict(row))


@driver_router.post("/routes/{route_id}/start", response_model=RouteOut)
def driver_start_route(
    route_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _require_driver(current_user)
    row = db.execute(
        text("SELECT status FROM driver_routes WHERE id = :rid AND driver_id = :uid"),
        {"rid": route_id, "uid": current_user.id}
    ).first()
    if not row:
        raise HTTPException(status_code=404, detail="Маршрут не найден")
    if row[0] != "planned":
        raise HTTPException(status_code=400, detail=f"Маршрут уже '{row[0]}'")
    db.execute(text("UPDATE driver_routes SET status = 'active', started_at = NOW() WHERE id = :rid"), {"rid": route_id})
    db.commit()
    res = db.execute(text("SELECT * FROM driver_routes WHERE id = :rid"), {"rid": route_id}).mappings().first()
    return _route_to_out(db, dict(res))


@driver_router.post("/routes/{route_id}/points/{point_id}/arrived", response_model=RoutePointOut)
def driver_point_arrived(
    route_id: UUID,
    point_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _require_driver(current_user)
    own = db.execute(text("SELECT 1 FROM driver_routes WHERE id = :rid AND driver_id = :uid"),
                     {"rid": route_id, "uid": current_user.id}).first()
    if not own:
        raise HTTPException(status_code=404, detail="Маршрут не ваш")
    pt = db.execute(text("SELECT status FROM driver_route_points WHERE id = :pid AND route_id = :rid"),
                    {"pid": point_id, "rid": route_id}).first()
    if not pt:
        raise HTTPException(status_code=404, detail="Точка не найдена")
    if pt[0] != "pending":
        raise HTTPException(status_code=400, detail=f"Точка уже '{pt[0]}'")
    db.execute(text("UPDATE driver_route_points SET status = 'arrived', arrived_at = NOW() WHERE id = :pid"), {"pid": point_id})
    # Если первая точка — активируем маршрут
    db.execute(text("UPDATE driver_routes SET status = 'active', started_at = COALESCE(started_at, NOW()) WHERE id = :rid AND status = 'planned'"), {"rid": route_id})
    db.commit()
    out = db.execute(text("SELECT * FROM driver_route_points WHERE id = :pid"), {"pid": point_id}).mappings().first()
    return dict(out)


@driver_router.post("/routes/{route_id}/points/{point_id}/delivered", response_model=RoutePointOut)
def driver_point_delivered(
    route_id: UUID,
    point_id: UUID,
    photo_url: Optional[str] = None,
    notes: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _require_driver(current_user)
    own = db.execute(text("SELECT 1 FROM driver_routes WHERE id = :rid AND driver_id = :uid"),
                     {"rid": route_id, "uid": current_user.id}).first()
    if not own:
        raise HTTPException(status_code=404, detail="Маршрут не ваш")
    pt = db.execute(text("SELECT status FROM driver_route_points WHERE id = :pid AND route_id = :rid"),
                    {"pid": point_id, "rid": route_id}).first()
    if not pt:
        raise HTTPException(status_code=404, detail="Точка не найдена")
    if pt[0] not in ("pending", "arrived"):
        raise HTTPException(status_code=400, detail=f"Точка уже '{pt[0]}'")
    db.execute(text("""
        UPDATE driver_route_points SET
            status = 'delivered',
            delivered_at = NOW(),
            photo_url = COALESCE(:ph, photo_url),
            notes = COALESCE(:n, notes)
        WHERE id = :pid
    """), {"pid": point_id, "ph": photo_url, "n": notes})
    db.commit()
    _refresh_route_counters(db, route_id)
    # Также: если эта точка attached к delivery_request — пометить delivered
    db.execute(text("""
        UPDATE delivery_requests SET status = 'delivered', delivered_at = NOW(), updated_at = NOW()
        WHERE assigned_point_id = :pid AND status IN ('assigned', 'in_transit')
    """), {"pid": point_id})
    db.commit()
    out = db.execute(text("SELECT * FROM driver_route_points WHERE id = :pid"), {"pid": point_id}).mappings().first()
    return dict(out)


@driver_router.post("/routes/{route_id}/points/{point_id}/skip", response_model=RoutePointOut)
def driver_point_skip(
    route_id: UUID,
    point_id: UUID,
    payload: SkipPointIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _require_driver(current_user)
    own = db.execute(text("SELECT 1 FROM driver_routes WHERE id = :rid AND driver_id = :uid"),
                     {"rid": route_id, "uid": current_user.id}).first()
    if not own:
        raise HTTPException(status_code=404, detail="Маршрут не ваш")
    db.execute(text("""
        UPDATE driver_route_points SET status = 'skipped', skip_reason = :r
        WHERE id = :pid AND route_id = :rid
    """), {"pid": point_id, "rid": route_id, "r": payload.reason})
    db.commit()
    _refresh_route_counters(db, route_id)
    out = db.execute(text("SELECT * FROM driver_route_points WHERE id = :pid"), {"pid": point_id}).mappings().first()
    return dict(out)


@driver_router.post("/routes/{route_id}/complete", response_model=RouteOut)
def driver_complete_route(
    route_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    _require_driver(current_user)
    own = db.execute(text("SELECT status FROM driver_routes WHERE id = :rid AND driver_id = :uid"),
                     {"rid": route_id, "uid": current_user.id}).first()
    if not own:
        raise HTTPException(status_code=404, detail="Маршрут не ваш")
    if own[0] == "completed":
        raise HTTPException(status_code=400, detail="Уже завершён")
    db.execute(text("UPDATE driver_routes SET status = 'completed', completed_at = NOW() WHERE id = :rid"), {"rid": route_id})
    db.commit()
    res = db.execute(text("SELECT * FROM driver_routes WHERE id = :rid"), {"rid": route_id}).mappings().first()
    return _route_to_out(db, dict(res))


@driver_router.get("/history", response_model=List[RouteOut])
def driver_history(
    days: int = Query(7, ge=1, le=90),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Прошлые маршруты водителя."""
    _require_driver(current_user)
    rows = db.execute(text("""
        SELECT * FROM driver_routes
        WHERE driver_id = :uid
          AND planned_date >= CURRENT_DATE - (:d || ' days')::INTERVAL
        ORDER BY planned_date DESC, created_at DESC
    """), {"uid": current_user.id, "d": days}).mappings().all()
    return [_route_to_out(db, dict(r), with_points=False) for r in rows]


@driver_router.get("/dashboard")
def driver_dashboard(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Сводка для водителя."""
    _require_driver(current_user)
    active = db.execute(text("""
        SELECT id, total_points, completed_points FROM driver_routes
        WHERE driver_id = :uid AND status = 'active' LIMIT 1
    """), {"uid": current_user.id}).first()
    today_count = db.execute(text("""
        SELECT COUNT(*) FROM driver_routes
        WHERE driver_id = :uid AND planned_date = CURRENT_DATE
    """), {"uid": current_user.id}).scalar() or 0
    return {
        "active_route_id": active[0] if active else None,
        "progress": f"{active[2]}/{active[1]}" if active else None,
        "routes_today": today_count,
    }
