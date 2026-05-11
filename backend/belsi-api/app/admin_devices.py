"""Devices/versions tracking endpoint.

GET /admin/devices  — для координатора/куратора, показывает версии приложений
                       по пользователям (когда последний раз видели, какая версия).
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from sqlalchemy import text as sa_text

from .db import get_db
from .auth import get_current_user
from .models import User

router = APIRouter(prefix="/admin", tags=["admin"])


@router.get("/devices")
def list_devices(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Список устройств с версиями приложения.

    Доступ: coordinator, curator, foreman.
    Возвращает: phone, имя, роль, последняя версия, build, платформа,
                last_seen, app_version_seen_at.
    """
    if current_user.role not in ("coordinator", "curator", "foreman"):
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Forbidden: requires coordinator/curator/foreman role",
        )

    rows = db.execute(
        sa_text("""
            SELECT
                u.id::text                AS id,
                u.phone                   AS phone,
                COALESCE(u.first_name,'') || ' ' || COALESCE(u.last_name,'') AS full_name,
                u.role                    AS role,
                u.app_version             AS app_version,
                u.app_build               AS app_build,
                u.app_platform            AS app_platform,
                u.app_version_seen_at     AS app_version_seen_at,
                u.last_seen               AS last_seen,
                CASE
                    WHEN u.last_seen IS NULL THEN 'never'
                    WHEN u.last_seen > NOW() - INTERVAL '5 minutes' THEN 'online'
                    WHEN u.last_seen > NOW() - INTERVAL '1 hour'   THEN 'recent'
                    WHEN u.last_seen > NOW() - INTERVAL '24 hours' THEN 'today'
                    ELSE 'inactive'
                END AS activity
            FROM users u
            WHERE u.role IN ('installer','foreman','curator','coordinator')
            ORDER BY u.last_seen DESC NULLS LAST
        """)
    ).fetchall()

    return {
        "count": len(rows),
        "devices": [
            {
                "id": r.id,
                "phone": r.phone,
                "full_name": (r.full_name or "").strip() or None,
                "role": r.role,
                "app_version": r.app_version,
                "app_build": r.app_build,
                "app_platform": r.app_platform,
                "app_version_seen_at": r.app_version_seen_at.isoformat() if r.app_version_seen_at else None,
                "last_seen": r.last_seen.isoformat() if r.last_seen else None,
                "activity": r.activity,
            }
            for r in rows
        ],
    }


@router.get("/devices/stats")
def device_stats(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Сводка: сколько устройств на какой версии."""
    if current_user.role not in ("coordinator", "curator", "foreman"):
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Forbidden")

    by_version = db.execute(
        sa_text("""
            SELECT
                COALESCE(app_version, '(unknown)') AS version,
                COALESCE(app_build, 0)             AS build,
                COALESCE(app_platform, '(unknown)') AS platform,
                COUNT(*)                            AS users,
                COUNT(*) FILTER (WHERE last_seen > NOW() - INTERVAL '24 hours') AS active_24h,
                MAX(app_version_seen_at)            AS latest_seen
            FROM users
            WHERE role IN ('installer','foreman','curator','coordinator')
            GROUP BY version, build, platform
            ORDER BY users DESC, latest_seen DESC NULLS LAST
        """)
    ).fetchall()

    return {
        "by_version": [
            {
                "version": r.version,
                "build": r.build,
                "platform": r.platform,
                "users": r.users,
                "active_24h": r.active_24h,
                "latest_seen": r.latest_seen.isoformat() if r.latest_seen else None,
            }
            for r in by_version
        ],
    }
