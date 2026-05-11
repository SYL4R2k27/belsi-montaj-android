"""
Brigade + Facility (production) — endpoints для ролей SeniorWorker и ProductionChief.

Brigade (бригада):
- Старший работник видит свою бригаду (членов, статусы, текущие смены).
- Начальник производства видит ВСЕ бригады своей фабрики.
- CRUD только у Chief; SeniorWorker — read + статусы.

Facility (фабрика):
- ProductionChief видит дашборд: метрики, активные смены, простои, партии.
- Метрики считаются on-the-fly из существующих таблиц.
"""
from __future__ import annotations
from datetime import datetime, date, timezone, timedelta
from typing import Optional, List
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User

router = APIRouter(prefix="/production", tags=["production-brigade-facility"])


# ============================================================
# Schemas
# ============================================================

class BrigadeMemberOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    user_id: UUID
    full_name: str
    role: str  # глобальная роль юзера (worker / senior_worker / engineer)
    role_in_brigade: str  # роль внутри бригады
    phone: Optional[str] = None
    avatar_url: Optional[str] = None
    is_on_shift: bool = False  # есть ли активная смена
    on_pause: bool = False  # на паузе сейчас
    on_idle: bool = False
    idle_reason: Optional[str] = None


class BrigadeOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    name: str
    facility_id: UUID
    senior_worker_id: Optional[UUID] = None
    senior_name: Optional[str] = None
    members_count: int = 0
    active_count: int = 0  # на смене сейчас
    idle_count: int = 0  # на простое
    created_at: datetime


class BrigadeCreate(BaseModel):
    name: str
    facility_id: UUID
    senior_worker_id: Optional[UUID] = None


class BrigadeMemberAdd(BaseModel):
    user_id: UUID
    role_in_brigade: str = "worker"


class FacilityDashboardOut(BaseModel):
    facility_id: UUID
    facility_name: str
    # Партии
    batches_total: int
    batches_in_production: int
    batches_ready_to_ship: int
    batches_completed_today: int
    # Бригады
    brigades_count: int
    workers_total: int
    workers_on_shift: int
    workers_on_pause: int
    workers_on_idle: int
    # Время
    idle_hours_today: float
    work_hours_today: float
    # Заявки на материалы
    material_orders_pending: int


class FacilityOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    id: UUID
    name: str
    address: Optional[str] = None


# ============================================================
# Helpers
# ============================================================

def _user_full_name(user_row) -> str:
    """Безопасно собрать full_name из строки user."""
    fn = user_row.get("first_name") or ""
    ln = user_row.get("last_name") or ""
    name = f"{fn} {ln}".strip()
    return name or user_row.get("phone") or "Без имени"


def _get_brigade_for_user(db: Session, user_id: UUID) -> Optional[dict]:
    """Найти бригаду где user — старший либо член."""
    # Сначала ищем как senior
    row = db.execute(
        text("SELECT * FROM brigades WHERE senior_worker_id = :uid LIMIT 1"),
        {"uid": str(user_id)},
    ).mappings().first()
    if row:
        return dict(row)
    # Иначе ищем как member
    row = db.execute(
        text("""
            SELECT b.* FROM brigades b
            JOIN brigade_members m ON m.brigade_id = b.id
            WHERE m.user_id = :uid LIMIT 1
        """),
        {"uid": str(user_id)},
    ).mappings().first()
    return dict(row) if row else None


# ============================================================
# Brigade — список и CRUD
# ============================================================

@router.get("/brigades", response_model=List[BrigadeOut])
def list_brigades(
    facility_id: Optional[UUID] = Query(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Список бригад.
    - SeniorWorker видит только свою (где он senior или member).
    - ProductionChief видит все на своей фабрике.
    - Куратор/координатор/админ — всё.
    """
    role = current_user.role

    where_clauses = []
    params = {}
    if facility_id:
        where_clauses.append("b.facility_id = :facility_id")
        params["facility_id"] = str(facility_id)

    if role == "senior_worker":
        where_clauses.append("(b.senior_worker_id = :uid OR EXISTS (SELECT 1 FROM brigade_members m WHERE m.brigade_id = b.id AND m.user_id = :uid))")
        params["uid"] = str(current_user.id)
    elif role == "worker":
        where_clauses.append("EXISTS (SELECT 1 FROM brigade_members m WHERE m.brigade_id = b.id AND m.user_id = :uid)")
        params["uid"] = str(current_user.id)
    # production_chief / curator / coordinator — без ограничений

    where_sql = ("WHERE " + " AND ".join(where_clauses)) if where_clauses else ""

    rows = db.execute(
        text(f"""
            SELECT b.*,
                   (u.first_name || ' ' || COALESCE(u.last_name, '')) AS senior_name,
                   (SELECT COUNT(*) FROM brigade_members m WHERE m.brigade_id = b.id) AS members_count,
                   (SELECT COUNT(*) FROM brigade_members m
                    JOIN shifts s ON s.user_id = m.user_id AND s.status = 'active'
                    WHERE m.brigade_id = b.id) AS active_count,
                   (SELECT COUNT(*) FROM brigade_members m
                    JOIN shifts s ON s.user_id = m.user_id AND s.status = 'active'
                    JOIN shift_pauses p ON p.shift_id = s.id AND p.ended_at IS NULL
                    WHERE m.brigade_id = b.id AND p.reason IS NOT NULL AND p.reason != '') AS idle_count
            FROM brigades b
            LEFT JOIN users u ON u.id = b.senior_worker_id
            {where_sql}
            ORDER BY b.created_at DESC
        """),
        params,
    ).mappings().all()

    return [BrigadeOut(**dict(r)) for r in rows]


@router.post("/brigades", response_model=BrigadeOut, status_code=201)
def create_brigade(
    payload: BrigadeCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Создать бригаду — только Chief или Curator."""
    if current_user.role not in ("production_chief", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Только начальник производства может создавать бригады")

    row = db.execute(
        text("""
            INSERT INTO brigades (name, facility_id, senior_worker_id)
            VALUES (:name, :fid, :sid)
            RETURNING *
        """),
        {
            "name": payload.name,
            "fid": str(payload.facility_id),
            "sid": str(payload.senior_worker_id) if payload.senior_worker_id else None,
        },
    ).mappings().first()
    db.commit()

    return BrigadeOut(
        id=row["id"],
        name=row["name"],
        facility_id=row["facility_id"],
        senior_worker_id=row["senior_worker_id"],
        senior_name=None,
        members_count=0,
        active_count=0,
        idle_count=0,
        created_at=row["created_at"],
    )


@router.get("/brigades/{brigade_id}/members", response_model=List[BrigadeMemberOut])
def get_brigade_members(
    brigade_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список членов бригады с актуальным статусом смены/паузы."""
    rows = db.execute(
        text("""
            SELECT m.role_in_brigade,
                   u.id AS user_id, u.first_name, u.last_name, u.phone, u.role,
                   u.avatar_url,
                   s.id AS shift_id,
                   p.reason AS pause_reason,
                   p.ended_at AS pause_ended
            FROM brigade_members m
            JOIN users u ON u.id = m.user_id
            LEFT JOIN shifts s ON s.user_id = u.id AND s.status = 'active'
            LEFT JOIN shift_pauses p ON p.shift_id = s.id AND p.ended_at IS NULL
            WHERE m.brigade_id = :bid
            ORDER BY m.role_in_brigade, u.last_name
        """),
        {"bid": str(brigade_id)},
    ).mappings().all()

    out = []
    for r in rows:
        on_shift = r["shift_id"] is not None
        on_pause = on_shift and r["pause_reason"] is None and r["pause_ended"] is None and r["shift_id"] is not None
        # Re-check: if pause_reason is None it might mean no row or NULL reason
        # Simplify: pause exists iff shift_id row was joined with non-null pause start (we can't tell from above).
        # Use proper check below:
        on_pause = False
        on_idle = False
        idle_reason = None
        if on_shift:
            # Re-query for cleaner state of pause
            pr = db.execute(
                text("""
                    SELECT reason FROM shift_pauses
                    WHERE shift_id = :sid AND ended_at IS NULL LIMIT 1
                """),
                {"sid": str(r["shift_id"])},
            ).mappings().first()
            if pr:
                if pr["reason"] and pr["reason"].strip():
                    on_idle = True
                    idle_reason = pr["reason"]
                else:
                    on_pause = True

        out.append(BrigadeMemberOut(
            user_id=r["user_id"],
            full_name=_user_full_name(r),
            role=r["role"] or "worker",
            role_in_brigade=r["role_in_brigade"] or "worker",
            phone=r["phone"],
            avatar_url=r["avatar_url"],
            is_on_shift=on_shift,
            on_pause=on_pause,
            on_idle=on_idle,
            idle_reason=idle_reason,
        ))
    return out


@router.post("/brigades/{brigade_id}/members", status_code=201)
def add_brigade_member(
    brigade_id: UUID,
    payload: BrigadeMemberAdd,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Добавить члена в бригаду — Chief или senior бригады."""
    if current_user.role not in ("production_chief", "senior_worker", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Недостаточно прав")

    # Если Senior — проверяем что он senior именно этой бригады
    if current_user.role == "senior_worker":
        row = db.execute(
            text("SELECT senior_worker_id FROM brigades WHERE id = :bid"),
            {"bid": str(brigade_id)},
        ).mappings().first()
        if not row or row["senior_worker_id"] != current_user.id:
            raise HTTPException(status_code=403, detail="Можно управлять только своей бригадой")

    db.execute(
        text("""
            INSERT INTO brigade_members (brigade_id, user_id, role_in_brigade)
            VALUES (:bid, :uid, :role)
            ON CONFLICT (brigade_id, user_id) DO UPDATE SET role_in_brigade = :role
        """),
        {"bid": str(brigade_id), "uid": str(payload.user_id), "role": payload.role_in_brigade},
    )
    db.commit()
    return {"ok": True}


@router.delete("/brigades/{brigade_id}/members/{user_id}", status_code=204)
def remove_brigade_member(
    brigade_id: UUID,
    user_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    if current_user.role not in ("production_chief", "senior_worker", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Недостаточно прав")

    db.execute(
        text("DELETE FROM brigade_members WHERE brigade_id = :bid AND user_id = :uid"),
        {"bid": str(brigade_id), "uid": str(user_id)},
    )
    db.commit()
    return None


# ============================================================
# «Моя бригада» — для SeniorWorker
# ============================================================

@router.get("/brigades/mine", response_model=Optional[BrigadeOut])
def get_my_brigade(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Бригада текущего пользователя (если он senior или member)."""
    b = _get_brigade_for_user(db, current_user.id)
    if not b:
        return None

    members_count = db.execute(
        text("SELECT COUNT(*) AS c FROM brigade_members WHERE brigade_id = :bid"),
        {"bid": str(b["id"])},
    ).mappings().first()
    active_count = db.execute(
        text("""
            SELECT COUNT(*) AS c FROM brigade_members m
            JOIN shifts s ON s.user_id = m.user_id AND s.status = 'active'
            WHERE m.brigade_id = :bid
        """),
        {"bid": str(b["id"])},
    ).mappings().first()
    idle_count = db.execute(
        text("""
            SELECT COUNT(*) AS c FROM brigade_members m
            JOIN shifts s ON s.user_id = m.user_id AND s.status = 'active'
            JOIN shift_pauses p ON p.shift_id = s.id AND p.ended_at IS NULL
            WHERE m.brigade_id = :bid AND p.reason IS NOT NULL AND p.reason != ''
        """),
        {"bid": str(b["id"])},
    ).mappings().first()

    senior_name = None
    if b["senior_worker_id"]:
        senior_row = db.execute(
            text("SELECT first_name, last_name, phone FROM users WHERE id = :uid"),
            {"uid": str(b["senior_worker_id"])},
        ).mappings().first()
        if senior_row:
            senior_name = _user_full_name(senior_row)

    return BrigadeOut(
        id=b["id"],
        name=b["name"],
        facility_id=b["facility_id"],
        senior_worker_id=b["senior_worker_id"],
        senior_name=senior_name,
        members_count=members_count["c"] or 0,
        active_count=active_count["c"] or 0,
        idle_count=idle_count["c"] or 0,
        created_at=b["created_at"],
    )


# ============================================================
# Facility — список и дашборд
# ============================================================

@router.get("/facilities", response_model=List[FacilityOut])
def list_facilities(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список фабрик. Используем site_objects с object_type='production_facility' (если есть)."""
    rows = db.execute(
        text("""
            SELECT id, name,
                   COALESCE(address, NULL) AS address
            FROM site_objects
            WHERE COALESCE(object_type, 'installation_target') IN ('production_facility', 'facility')
               OR name ILIKE '%фабрика%'
               OR name ILIKE '%углич%'
            ORDER BY name
        """),
    ).mappings().all()
    return [FacilityOut(**dict(r)) for r in rows]


@router.get("/facility/{facility_id}/dashboard", response_model=FacilityDashboardOut)
def facility_dashboard(
    facility_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Дашборд для ProductionChief — агрегированные метрики фабрики."""
    fac = db.execute(
        text("SELECT id, name FROM site_objects WHERE id = :id"),
        {"id": str(facility_id)},
    ).mappings().first()
    if not fac:
        raise HTTPException(status_code=404, detail="Фабрика не найдена")

    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)

    # Партии
    batches = db.execute(
        text("""
            SELECT
                COUNT(*) AS total,
                COUNT(*) FILTER (WHERE status = 'in_production') AS in_prod,
                COUNT(*) FILTER (WHERE status = 'ready_to_ship') AS ready,
                COUNT(*) FILTER (WHERE status = 'installed' AND updated_at >= :today) AS done_today
            FROM production_batches
            WHERE source_facility_id = :fid
        """),
        {"fid": str(facility_id), "today": today_start},
    ).mappings().first()

    # Бригады и рабочие
    brig = db.execute(
        text("""
            SELECT
                COUNT(DISTINCT b.id) AS brigades,
                COUNT(DISTINCT m.user_id) AS workers
            FROM brigades b
            LEFT JOIN brigade_members m ON m.brigade_id = b.id
            WHERE b.facility_id = :fid
        """),
        {"fid": str(facility_id)},
    ).mappings().first()

    # Активные на смене
    active = db.execute(
        text("""
            SELECT COUNT(DISTINCT m.user_id) AS c
            FROM brigade_members m
            JOIN brigades b ON b.id = m.brigade_id
            JOIN shifts s ON s.user_id = m.user_id AND s.status = 'active'
            WHERE b.facility_id = :fid
        """),
        {"fid": str(facility_id)},
    ).mappings().first()

    # На паузе / простое
    paused = db.execute(
        text("""
            SELECT
                COUNT(*) FILTER (WHERE p.reason IS NULL OR p.reason = '') AS paused,
                COUNT(*) FILTER (WHERE p.reason IS NOT NULL AND p.reason != '') AS idled
            FROM brigade_members m
            JOIN brigades b ON b.id = m.brigade_id
            JOIN shifts s ON s.user_id = m.user_id AND s.status = 'active'
            JOIN shift_pauses p ON p.shift_id = s.id AND p.ended_at IS NULL
            WHERE b.facility_id = :fid
        """),
        {"fid": str(facility_id)},
    ).mappings().first()

    # Часы за день
    hours = db.execute(
        text("""
            SELECT
                COALESCE(SUM(idle_seconds), 0)::float / 3600.0 AS idle_h,
                COALESCE(SUM(GREATEST(total_seconds, 0)), 0)::float / 3600.0 AS work_h
            FROM shifts s
            WHERE s.start_at >= :today
              AND s.user_id IN (
                  SELECT m.user_id FROM brigade_members m
                  JOIN brigades b ON b.id = m.brigade_id
                  WHERE b.facility_id = :fid
              )
        """),
        {"fid": str(facility_id), "today": today_start},
    ).mappings().first()

    # Заявки на материалы
    orders = db.execute(
        text("""
            SELECT COUNT(*) AS pending FROM material_orders
            WHERE facility_id = :fid AND status = 'pending'
        """),
        {"fid": str(facility_id)},
    ).mappings().first()

    return FacilityDashboardOut(
        facility_id=facility_id,
        facility_name=fac["name"],
        batches_total=batches["total"] or 0,
        batches_in_production=batches["in_prod"] or 0,
        batches_ready_to_ship=batches["ready"] or 0,
        batches_completed_today=batches["done_today"] or 0,
        brigades_count=brig["brigades"] or 0,
        workers_total=brig["workers"] or 0,
        workers_on_shift=active["c"] or 0,
        workers_on_pause=paused["paused"] or 0 if paused else 0,
        workers_on_idle=paused["idled"] or 0 if paused else 0,
        idle_hours_today=round(hours["idle_h"] or 0.0, 1),
        work_hours_today=round(hours["work_h"] or 0.0, 1),
        material_orders_pending=orders["pending"] or 0,
    )
