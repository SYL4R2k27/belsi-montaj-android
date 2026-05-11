from __future__ import annotations

from typing import Optional, List
from datetime import datetime
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import text
from sqlalchemy.orm import Session

from .db import get_db
from .auth import get_current_user

router = APIRouter(tags=["photos_feed"])

def _role(u) -> str:
    r = getattr(u, "role", "") or ""
    return r.strip().lower()

@router.get("/foreman/photos/latest")
def foreman_latest_photos(
    limit: int = Query(50, ge=1, le=200),
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user),
):
    # только бригадир
    if _role(current_user) != "foreman":
        raise HTTPException(status_code=403, detail="Only foreman can access this endpoint")

    sql = text("""
        SELECT
            sp.id,
            sp.shift_id,
            sp.hour_label,
            sp.status,
            sp.comment,
            sp.photo_url,
            sp.created_at,

            u.id   AS installer_user_id,
            u.phone AS installer_phone,

            s.start_at  AS shift_start_at,
            s.finish_at AS shift_finish_at,
            s.status    AS shift_status

        FROM public.shift_photos sp
        JOIN public.shifts s ON s.id = sp.shift_id
        JOIN public.users  u ON u.id = s.user_id
        JOIN public.foreman_invites fi
            ON fi.installer_phone = u.phone
           AND fi.foreman_phone = :foreman_phone
           AND fi.status = 'accepted'
        ORDER BY sp.created_at DESC
        LIMIT :limit
    """)

    rows = db.execute(sql, {"foreman_phone": current_user.phone, "limit": limit}).mappings().all()
    return [dict(r) for r in rows]


@router.get("/curator/photos/latest")
def curator_latest_photos(
    limit: int = Query(100, ge=1, le=500),
    installer_phone: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user),
):
    # только куратор
    if _role(current_user) != "curator":
        raise HTTPException(status_code=403, detail="Only curator can access this endpoint")

    base = """
        SELECT
            sp.id,
            sp.shift_id,
            sp.hour_label,
            sp.status,
            sp.comment,
            sp.photo_url,
            sp.created_at,

            u.id   AS installer_user_id,
            u.phone AS installer_phone,

            s.start_at  AS shift_start_at,
            s.finish_at AS shift_finish_at,
            s.status    AS shift_status

        FROM public.shift_photos sp
        JOIN public.shifts s ON s.id = sp.shift_id
        JOIN public.users  u ON u.id = s.user_id
    """

    where = ""
    params = {"limit": limit}
    if installer_phone:
        where = " WHERE u.phone = :installer_phone "
        params["installer_phone"] = installer_phone

    sql = text(base + where + " ORDER BY sp.created_at DESC LIMIT :limit")
    rows = db.execute(sql, params).mappings().all()
    return [dict(r) for r in rows]
