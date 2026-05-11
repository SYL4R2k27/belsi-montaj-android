import asyncio
import logging
from datetime import datetime, timedelta, timezone
try:
    from zoneinfo import ZoneInfo
    MSK = ZoneInfo('Europe/Moscow')
except ImportError:
    MSK = timezone(timedelta(hours=3))
from sqlalchemy import func, text as sa_text
from .db import SessionLocal
from .models import Shift, ShiftPhoto, User

logger = logging.getLogger("shift_closer")

# Автозакрытие смен через 12 часов без фото (рабочий день + запас)
IDLE_TIMEOUT_HOURS = 12
# Если у пользователя last_seen свежее этого порога — считаем живым
LIVE_THRESHOLD_MINUTES = 30
CHECK_INTERVAL_MINUTES = 30


def close_idle_shifts():
    """Close shifts where the last photo was uploaded more than IDLE_TIMEOUT_HOURS ago.

    IMPORTANT: All timestamps in the DB are stored as UTC+3 (Moscow time).
    We must compare using the same timezone to avoid negative durations.

    SAFETY WINDOW (2026-04-29): автоматическое закрытие работает только
    в ночное окно 23:00–07:00 МСК, чтобы случайно не закрыть живую смену
    во время рабочего дня.
    """
    now = datetime.now(MSK)

    # Окно технического обслуживания: 23:00–07:00 МСК
    if not (now.hour >= 23 or now.hour < 7):
        logger.debug(
            f"shift_closer: skipping run outside maintenance window "
            f"(MSK hour={now.hour}, allowed: 23–07)"
        )
        return

    db = SessionLocal()
    try:
        cutoff = now - timedelta(hours=IDLE_TIMEOUT_HOURS)

        # FIX(2026-04-30): SELECT FOR UPDATE SKIP LOCKED — пропускаем смены,
        # которые в этот момент закрывает пользователь через /shifts/finish.
        active_shifts = (
            db.query(Shift)
            .filter(Shift.status == "active", Shift.finish_at.is_(None))
            .with_for_update(skip_locked=True)
            .all()
        )

        if not active_shifts:
            logger.debug("No active shifts found")
            return

        closed_count = 0
        skipped_count = 0

        for shift in active_shifts:
            try:
                # FIX(2026-04-29): Защита от закрытия живой смены —
                # проверяем last_seen пользователя. Если он был активен
                # < LIVE_THRESHOLD_MINUTES минут назад — пропускаем.
                user = db.query(User).filter(User.id == shift.user_id).first()
                if user and user.last_seen:
                    last_seen = user.last_seen
                    if last_seen.tzinfo is None:
                        last_seen = last_seen.replace(tzinfo=timezone.utc)
                    if last_seen > now - timedelta(minutes=LIVE_THRESHOLD_MINUTES):
                        logger.info(
                            f"Skipping shift {shift.id}: user {user.phone} is live "
                            f"(last_seen={last_seen}, threshold={LIVE_THRESHOLD_MINUTES}m)"
                        )
                        skipped_count += 1
                        continue

                # FIX(2026-04-29): не закрывать смены не-монтажников по правилу
                # «нет фото N часов» — у бригадиров/кураторов/координаторов
                # фото отсутствуют по дизайну. Их смены закрываются по таймауту
                # неактивности (last_seen старше 24 часов) — закрытие отдельно.
                if user and user.role and user.role != "installer":
                    if user.last_seen and user.last_seen.replace(
                        tzinfo=timezone.utc if user.last_seen.tzinfo is None else user.last_seen.tzinfo
                    ) > now - timedelta(hours=24):
                        logger.debug(
                            f"Skipping shift {shift.id}: user role={user.role}, "
                            f"active in last 24h (no photo rule does not apply)"
                        )
                        skipped_count += 1
                        continue
                    # если last_seen старее 24 ч — позволяем закрыть как просрочку

                # Find the last photo for this shift
                last_photo_at = (
                    db.query(func.max(ShiftPhoto.created_at))
                    .filter(ShiftPhoto.shift_id == shift.id)
                    .scalar()
                )

                # Make start_at timezone-aware if needed
                start_at = shift.start_at
                if start_at and start_at.tzinfo is None:
                    start_at = start_at.replace(tzinfo=MSK)

                # Make last_photo_at timezone-aware if needed
                if last_photo_at and last_photo_at.tzinfo is None:
                    last_photo_at = last_photo_at.replace(tzinfo=MSK)

                # Determine whether to close
                close_at = None
                if last_photo_at and last_photo_at < cutoff:
                    close_at = last_photo_at
                elif not last_photo_at and start_at and start_at < cutoff:
                    # FIX(2026-04-29): для смен без фото — close_at = start + 12h,
                    # иначе close_at == start_at и условие close_at<=start_at ниже
                    # бесконечно скипает запись.
                    close_at = start_at + timedelta(hours=IDLE_TIMEOUT_HOURS)

                if close_at is None:
                    continue

                # Make close_at timezone-aware if needed
                if close_at.tzinfo is None:
                    close_at = close_at.replace(tzinfo=MSK)

                # Calculate duration — MUST be positive
                duration_seconds = (close_at - start_at).total_seconds()
                if duration_seconds < 0:
                    logger.warning(
                        f"Skipping shift {shift.id}: negative duration "
                        f"({duration_seconds}s). start_at={start_at}, close_at={close_at}"
                    )
                    skipped_count += 1
                    continue

                # Also ensure finish_at > start_at (DB constraint)
                if close_at <= start_at:
                    logger.warning(
                        f"Skipping shift {shift.id}: close_at <= start_at. "
                        f"start_at={start_at}, close_at={close_at}"
                    )
                    skipped_count += 1
                    continue

                # FIX(2026-04-30): закрыть активные паузы — иначе зомби-паузы.
                db.execute(
                    sa_text("""
                        UPDATE shift_pauses
                           SET ended_at = :close_at,
                               duration_seconds = GREATEST(
                                   0,
                                   EXTRACT(EPOCH FROM (CAST(:close_at AS timestamptz) - started_at))::int
                               )
                         WHERE shift_id = :sid AND ended_at IS NULL
                    """),
                    {"close_at": close_at, "sid": str(shift.id)},
                )

                # FIX(2026-04-30): пересчитать pause_seconds из реальных пауз —
                # источник истины это shift_pauses.
                row = db.execute(
                    sa_text("""
                        SELECT COALESCE(SUM(duration_seconds), 0)::bigint AS s
                          FROM shift_pauses
                         WHERE shift_id = :sid AND duration_seconds IS NOT NULL
                    """),
                    {"sid": str(shift.id)},
                ).first()
                shift.pause_seconds = int(row.s if row else 0)

                pause_secs = shift.pause_seconds or 0
                idle_secs  = shift.idle_seconds or 0
                # FIX(2026-04-30): унифицированная формула с finish_shift —
                # total_seconds это РАБОЧЕЕ время (wall - pause - idle).
                work_seconds = max(0, int(duration_seconds) - pause_secs - idle_secs)

                shift.finish_at = close_at
                shift.status = "finished"
                shift.total_seconds = work_seconds
                shift.duration_hours = round(work_seconds / 3600, 4)
                shift.idle_reason = f"auto-closed: no photo for {IDLE_TIMEOUT_HOURS}+ hours"

                # Commit per-shift to avoid one bad shift blocking others
                db.commit()

                closed_count += 1
                logger.info(
                    f"Auto-closed shift {shift.id} for user {shift.user_id}. "
                    f"Last photo at: {last_photo_at}, closed at: {close_at}, "
                    f"duration: {round(duration_seconds/3600, 2)}h"
                )

            except Exception as e:
                logger.error(f"Error closing shift {shift.id}: {e}")
                db.rollback()
                skipped_count += 1

        if closed_count > 0 or skipped_count > 0:
            logger.info(
                f"Shift closer: closed={closed_count}, skipped={skipped_count}, "
                f"total_active={len(active_shifts)}"
            )

    except Exception as e:
        logger.error(f"Error in close_idle_shifts: {e}")
        db.rollback()
    finally:
        db.close()


async def shift_closer_loop():
    """Background loop that checks for idle shifts periodically."""
    logger.info(
        f"Shift closer started. Checking every {CHECK_INTERVAL_MINUTES} minutes, "
        f"timeout: {IDLE_TIMEOUT_HOURS} hours"
    )
    while True:
        try:
            await asyncio.to_thread(close_idle_shifts)
        except Exception as e:
            logger.error(f"Unexpected error in shift_closer_loop: {e}")
        await asyncio.sleep(CHECK_INTERVAL_MINUTES * 60)
