from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session
from sqlalchemy import func, and_
from typing import Optional
from datetime import datetime
from uuid import UUID
import logging

from .db import get_db
from .models import Shift, User
from .auth import get_current_user

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/reports", tags=["reports"])


@router.get("/shifts")
def get_shift_report(
    start_date: str = Query(..., description="Start date in YYYY-MM-DD format"),
    end_date: str = Query(..., description="End date in YYYY-MM-DD format"),
    user_id: Optional[UUID] = None,
    status: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Get shift report with filters for date range
    """
    try:
        start_dt = datetime.strptime(start_date, "%Y-%m-%d").date()
        end_dt = datetime.strptime(end_date, "%Y-%m-%d").date()

        logger.info(f"Fetching shift report from {start_dt} to {end_dt}")

        query = db.query(Shift).join(User, Shift.user_id == User.id)

        # Date filter using start_at
        query = query.filter(
            and_(
                func.date(Shift.start_at) >= start_dt,
                func.date(Shift.start_at) <= end_dt
            )
        )

        if user_id:
            query = query.filter(Shift.user_id == user_id)
        if status:
            query = query.filter(Shift.status == status.lower())

        shifts = query.all()
        logger.info(f"Found {len(shifts)} shifts")

        entries = []
        total_work_seconds = 0
        total_amount = 0.0

        for shift in shifts:
            user = shift.user

            # Calculate total seconds from start_at to finish_at
            if shift.start_at and shift.finish_at:
                total_seconds = int((shift.finish_at - shift.start_at).total_seconds())
            elif shift.duration_hours:
                total_seconds = int(float(shift.duration_hours) * 3600)
            else:
                total_seconds = 0

            if total_seconds < 0:
                total_seconds = 0

            # Берём паузы из shift.pause_seconds
            work_seconds = total_seconds - int(shift.pause_seconds or 0)
            pause_seconds = int(shift.pause_seconds or 0)
            idle_seconds = 0

            # Get hourly rate from shift (or default)
            hourly_rate = float(shift.hourly_rate) if shift.hourly_rate else 0.0

            work_hours = work_seconds / 3600.0
            amount = work_hours * hourly_rate

            total_work_seconds += work_seconds
            total_amount += amount

            # Build user display name
            user_full_name = None
            user_name = user.phone or ""
            if hasattr(user, 'full_name') and user.full_name:
                user_full_name = user.full_name
            elif user.first_name or user.last_name:
                parts = []
                if user.first_name:
                    parts.append(user.first_name)
                if user.last_name:
                    parts.append(user.last_name)
                user_full_name = " ".join(parts)

            entry = {
                "shiftId": str(shift.id),
                "userId": str(user.id),
                "userName": user.phone or "",
                "userFullName": user_full_name,
                "userPhone": user.phone or "",
                "shiftDate": shift.start_at.strftime("%Y-%m-%d"),
                "startTime": int(shift.start_at.timestamp()),
                "endTime": int(shift.finish_at.timestamp()) if shift.finish_at else None,
                "totalSeconds": total_seconds,
                "workSeconds": work_seconds,
                "pauseSeconds": pause_seconds,
                "idleSeconds": idle_seconds,
                "idleReason": None,
                "hourlyRate": hourly_rate,
                "totalAmount": round(amount, 2),
                "foremanId": None,
                "foremanName": None,
                "curatorId": None,
                "curatorName": None,
                "status": shift.status.upper() if shift.status else "ACTIVE"
            }

            entries.append(entry)

        response = {
            "entries": entries,
            "totalShifts": len(entries),
            "totalWorkHours": round(total_work_seconds / 3600.0, 2),
            "totalAmount": round(total_amount, 2),
            "periodStart": start_date,
            "periodEnd": end_date
        }

        logger.info(f"Report generated: {len(entries)} shifts, {response['totalWorkHours']} hours")
        return response

    except ValueError as e:
        logger.error(f"Invalid date format: {e}")
        raise HTTPException(status_code=400, detail=f"Invalid date format. Use YYYY-MM-DD: {str(e)}")
    except Exception as e:
        logger.error(f"Error generating shift report: {e}", exc_info=True)
        raise HTTPException(status_code=500, detail=f"Error generating report: {str(e)}")


@router.get("/foreman/shifts")
def get_foreman_shift_report(
    start_date: str = Query(..., description="Start date in YYYY-MM-DD format"),
    end_date: str = Query(..., description="End date in YYYY-MM-DD format"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    if current_user.role and current_user.role.lower() != "foreman":
        raise HTTPException(status_code=403, detail="Only foreman can access this endpoint")
    return get_shift_report(
        start_date=start_date,
        end_date=end_date,
        db=db,
        current_user=current_user
    )


@router.get("/curator/shifts")
def get_curator_shift_report(
    start_date: str = Query(..., description="Start date in YYYY-MM-DD format"),
    end_date: str = Query(..., description="End date in YYYY-MM-DD format"),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    if current_user.role and current_user.role.lower() != "curator":
        raise HTTPException(status_code=403, detail="Only curator can access this endpoint")
    return get_shift_report(
        start_date=start_date,
        end_date=end_date,
        db=db,
        current_user=current_user
    )
