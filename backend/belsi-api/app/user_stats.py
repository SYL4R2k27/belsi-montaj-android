"""User statistics endpoint.

GET /stats/users          — список монтажников с агрегатами за период
GET /stats/users/{id}     — детальная статистика одного пользователя

Параметр period:
  today | week | month | all

Учитывает scoping:
  - foreman    видит только своих монтажников (foreman_memberships)
  - coordinator видит монтажников через свои объекты (site_objects.coordinator_id)
  - curator    видит всех
  - installer  видит только себя
"""
from datetime import datetime, timedelta, timezone
from fastapi import APIRouter, Depends, HTTPException, Query, status
from sqlalchemy.orm import Session
from sqlalchemy import text as sa_text
from typing import Optional

from .db import get_db
from .auth import get_current_user
from .models import User

router = APIRouter(prefix="/stats", tags=["stats"])


def _period_clause(period: str) -> str:
    return {
        "today": "AND s.start_at >= date_trunc('day', NOW() AT TIME ZONE 'Europe/Moscow') AT TIME ZONE 'Europe/Moscow'",
        "week":  "AND s.start_at > NOW() - INTERVAL '7 days'",
        "month": "AND s.start_at > NOW() - INTERVAL '30 days'",
        "all":   "",
    }.get(period, "AND s.start_at > NOW() - INTERVAL '7 days'")


def _scope_user_ids(db: Session, current: User) -> Optional[list[str]]:
    """Возвращает список user_id, которых current может видеть.

    None = «без ограничений» (curator).
    Пустой список = «никого» (foreman без команды и т.п.).
    """
    role = (current.role or "").lower()
    uid = str(current.id)

    if role == "curator":
        return None  # видит всех

    if role == "coordinator":
        # FIX(2026-04-30): координатор видит монтажников только своих объектов.
        rows = db.execute(
            sa_text("""
                SELECT DISTINCT s.user_id::text
                  FROM shifts s
                  JOIN site_objects so ON so.id = s.site_object_id
                 WHERE so.coordinator_id = :uid
                UNION
                SELECT id::text FROM users WHERE id = :uid
            """),
            {"uid": uid},
        ).fetchall()
        return [r[0] for r in rows] or [uid]

    if role == "foreman":
        rows = db.execute(
            sa_text("""
                SELECT installer_user_id::text FROM foreman_memberships
                 WHERE foreman_user_id = :uid AND status = 'active'
                UNION
                SELECT :uid
            """),
            {"uid": uid},
        ).fetchall()
        return [r[0] for r in rows] or [uid]

    if role == "installer":
        return [uid]

    raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")


@router.get("/users")
def list_users_stats(
    period: str = Query("week", regex="^(today|week|month|all)$"),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    """Список пользователей с агрегатами за период (scoped по роли).

    Возвращает:
        users: [{phone, full_name, role, work_h, pause_h, idle_h,
                 shifts, photos, last_seen, app_version}]
        total_users
    """
    scope_ids = _scope_user_ids(db, current)
    period_sql = _period_clause(period)

    # WHERE-фильтр по списку id
    if scope_ids is None:
        scope_filter = ""
        params = {}
    else:
        if not scope_ids:
            return {"period": period, "total_users": 0, "users": []}
        # PostgreSQL IN с массивом
        scope_filter = "AND u.id = ANY(CAST(:ids AS uuid[]))"
        params = {"ids": scope_ids}

    rows = db.execute(
        sa_text(f"""
            WITH agg AS (
                SELECT
                    s.user_id,
                    SUM(COALESCE(s.total_seconds, 0))   AS work_s,
                    SUM(COALESCE(s.pause_seconds, 0))  AS pause_s,
                    SUM(COALESCE(s.idle_seconds, 0))   AS idle_s,
                    COUNT(*)                            AS shifts_cnt,
                    COUNT(*) FILTER (WHERE s.status = 'active') AS active_cnt
                  FROM shifts s
                 WHERE TRUE
                   {period_sql}
                 GROUP BY s.user_id
            ),
            ph AS (
                SELECT s.user_id, COUNT(p.id) AS photos
                  FROM shifts s
                  LEFT JOIN shift_photos p ON p.shift_id = s.id
                 WHERE TRUE {period_sql}
                 GROUP BY s.user_id
            )
            SELECT
                u.id::text AS id,
                u.phone,
                COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'') AS full_name,
                u.role,
                COALESCE(agg.work_s, 0)   AS work_s,
                COALESCE(agg.pause_s, 0)  AS pause_s,
                COALESCE(agg.idle_s, 0)   AS idle_s,
                COALESCE(agg.shifts_cnt, 0) AS shifts_cnt,
                COALESCE(agg.active_cnt, 0) AS active_cnt,
                COALESCE(ph.photos, 0)    AS photos,
                u.last_seen,
                u.app_version
              FROM users u
              LEFT JOIN agg ON agg.user_id = u.id
              LEFT JOIN ph  ON ph.user_id = u.id
             WHERE u.role IN ('installer','foreman','curator','coordinator')
               {scope_filter}
             ORDER BY work_s DESC NULLS LAST, u.phone
        """),
        params,
    ).fetchall()

    return {
        "period": period,
        "total_users": len(rows),
        "users": [
            {
                "id": r.id,
                "phone": r.phone,
                "full_name": (r.full_name or "").strip() or None,
                "role": r.role,
                "work_hours": round(float(r.work_s) / 3600.0, 2),
                "pause_hours": round(float(r.pause_s) / 3600.0, 2),
                "idle_hours": round(float(r.idle_s) / 3600.0, 2),
                "shifts": r.shifts_cnt,
                "active_now": r.active_cnt > 0,
                "photos": r.photos,
                "last_seen": r.last_seen.isoformat() if r.last_seen else None,
                "app_version": r.app_version,
            }
            for r in rows
        ],
    }


@router.get("/users/{user_id}")
def user_detail_stats(
    user_id: str,
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    """Детальная статистика одного пользователя по периодам today/week/month/all."""
    scope_ids = _scope_user_ids(db, current)
    if scope_ids is not None and user_id not in scope_ids:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    target = db.query(User).filter(User.id == user_id).first()
    if not target:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="User not found")

    out = {
        "id": str(target.id),
        "phone": target.phone,
        "full_name": ((target.first_name or "") + " " + (target.last_name or "")).strip() or None,
        "role": target.role,
        "last_seen": target.last_seen.isoformat() if target.last_seen else None,
        "app_version": target.app_version,
        "by_period": {},
    }

    for p in ("today", "week", "month", "all"):
        period_sql = _period_clause(p)
        row = db.execute(
            sa_text(f"""
                SELECT
                    SUM(COALESCE(s.total_seconds, 0)) AS work_s,
                    SUM(COALESCE(s.pause_seconds, 0)) AS pause_s,
                    SUM(COALESCE(s.idle_seconds, 0))  AS idle_s,
                    COUNT(*)                           AS shifts_cnt,
                    COUNT(*) FILTER (WHERE s.status='active') AS active_cnt,
                    (SELECT COUNT(*) FROM shift_photos p
                       JOIN shifts s2 ON s2.id = p.shift_id
                       WHERE s2.user_id = :uid {period_sql.replace("s.", "s2.")}) AS photos
                  FROM shifts s
                 WHERE s.user_id = :uid {period_sql}
            """),
            {"uid": user_id},
        ).first()

        out["by_period"][p] = {
            "work_hours":  round(float(row.work_s or 0) / 3600.0, 2),
            "pause_hours": round(float(row.pause_s or 0) / 3600.0, 2),
            "idle_hours":  round(float(row.idle_s or 0) / 3600.0, 2),
            "shifts": row.shifts_cnt or 0,
            "photos": row.photos or 0,
            "active_now": (row.active_cnt or 0) > 0,
        }

    return out


@router.get("/summary")
def overall_summary(
    period: str = Query("week", regex="^(today|week|month|all)$"),
    db: Session = Depends(get_db),
    current: User = Depends(get_current_user),
):
    """Общая статистика по всем пользователям в scope."""
    scope_ids = _scope_user_ids(db, current)
    period_sql = _period_clause(period)
    if scope_ids is None:
        scope_filter = ""
        params: dict = {}
    else:
        if not scope_ids:
            return {"period": period, "users": 0, "shifts": 0,
                    "work_hours": 0, "pause_hours": 0, "idle_hours": 0, "photos": 0}
        scope_filter = "AND s.user_id = ANY(CAST(:ids AS uuid[]))"
        params = {"ids": scope_ids}

    row = db.execute(
        sa_text(f"""
            SELECT
                COUNT(DISTINCT s.user_id)               AS users,
                COUNT(*)                                  AS shifts,
                SUM(COALESCE(s.total_seconds, 0))         AS work_s,
                SUM(COALESCE(s.pause_seconds, 0))         AS pause_s,
                SUM(COALESCE(s.idle_seconds, 0))          AS idle_s,
                (SELECT COUNT(*) FROM shift_photos p
                   JOIN shifts s2 ON s2.id = p.shift_id
                   WHERE TRUE {period_sql.replace("s.", "s2.")}
                     {scope_filter.replace("s.user_id", "s2.user_id")}) AS photos
              FROM shifts s
             WHERE TRUE {period_sql} {scope_filter}
        """),
        params,
    ).first()

    return {
        "period": period,
        "users":  row.users or 0,
        "shifts": row.shifts or 0,
        "work_hours":  round(float(row.work_s or 0) / 3600.0, 2),
        "pause_hours": round(float(row.pause_s or 0) / 3600.0, 2),
        "idle_hours":  round(float(row.idle_s or 0) / 3600.0, 2),
        "photos": row.photos or 0,
    }
