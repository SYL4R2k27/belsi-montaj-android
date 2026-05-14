"""
API для управления паузами смены
"""
from __future__ import annotations
from datetime import datetime, timezone
from typing import Optional, List
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, ConfigDict
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User

router = APIRouter(prefix="/shift", tags=["shift-pauses"])


class PauseOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)
    
    id: UUID
    shift_id: UUID
    started_at: datetime
    ended_at: Optional[datetime] = None
    reason: Optional[str] = None
    duration_seconds: Optional[int] = None


class StartPauseRequest(BaseModel):
    reason: Optional[str] = None


class EndPauseRequest(BaseModel):
    pass


def get_active_shift(db: Session, user_id: UUID):
    """Получить активную смену пользователя"""
    row = db.execute(
        text("SELECT * FROM shifts WHERE user_id = :user_id AND status = 'active' LIMIT 1"),
        {"user_id": str(user_id)}
    ).mappings().first()
    return dict(row) if row else None


def get_active_pause(db: Session, shift_id: UUID, for_update: bool = False):
    """Получить активную (незавершённую) паузу"""
    lock = " FOR UPDATE" if for_update else ""
    row = db.execute(
        text(f"SELECT * FROM shift_pauses WHERE shift_id = :shift_id AND ended_at IS NULL LIMIT 1{lock}"),
        {"shift_id": str(shift_id)}
    ).mappings().first()
    return dict(row) if row else None


@router.post("/pause/start", response_model=PauseOut)
def start_pause(
    payload: StartPauseRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Начать паузу в текущей смене
    """
    shift = get_active_shift(db, current_user.id)
    if not shift:
        raise HTTPException(status_code=400, detail="No active shift")
    
    # Проверяем нет ли уже активной паузы (FOR UPDATE для защиты от гонки)
    existing_pause = get_active_pause(db, shift["id"], for_update=True)
    if existing_pause:
        raise HTTPException(status_code=400, detail="Already on pause")
    
    # Создаём паузу
    row = db.execute(
        text("""
            INSERT INTO shift_pauses (shift_id, reason)
            VALUES (:shift_id, :reason)
            RETURNING *
        """),
        {"shift_id": str(shift["id"]), "reason": payload.reason}
    ).mappings().first()
    
    db.commit()
    return PauseOut(**row)


@router.post("/pause/end", response_model=PauseOut)
def end_pause(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Завершить паузу в текущей смене
    """
    shift = get_active_shift(db, current_user.id)
    if not shift:
        raise HTTPException(status_code=400, detail="No active shift")

    # FOR UPDATE блокирует строку паузы от конкурентных изменений
    pause = get_active_pause(db, shift["id"], for_update=True)
    if not pause:
        # FIX(2026-05-06) HOTFIX 1.2.6 + 1.3.0: idempotent end_pause.
        # Раньше: 400 "No active pause" → клиент получал onFailure → откатывал
        # optimistic UI обратно в isPaused=true → залипал в "вечной паузе"
        # (баг Курешова, Красавин — потерянный ответ на первом endPause).
        # Теперь: возвращаем синтетический закрытый объект с duration=0,
        # БЕЗ записи в БД. Клиент onSuccess → state.isPaused=false → разблокирован.
        # Безопасно: на клиенте durationSeconds?.toLong() ?: estimatedDuration → +0
        # к totalPauseSeconds, статистика не загрязняется.
        now = datetime.now(timezone.utc)
        return PauseOut(
            id=uuid4(),
            shift_id=shift["id"],
            started_at=now,
            ended_at=now,
            duration_seconds=0,
            reason=None,
        )

    # Вычисляем длительность
    started_at = pause["started_at"]
    ended_at = datetime.now(timezone.utc)
    duration_seconds = int((ended_at - started_at).total_seconds())
    
    # Завершаем паузу
    row = db.execute(
        text("""
            UPDATE shift_pauses
            SET ended_at = :ended_at, duration_seconds = :duration
            WHERE id = :id
            RETURNING *
        """),
        {"id": str(pause["id"]), "ended_at": ended_at, "duration": duration_seconds}
    ).mappings().first()
    
    # Обновляем общее время в смене (проверяем reason для правильной колонки)
    pause_reason = pause.get("reason")
    if pause_reason and pause_reason.strip():
        # Есть причина — это простой (idle)
        db.execute(
            text("""
                UPDATE shifts
                SET idle_seconds = COALESCE(idle_seconds, 0) + :duration
                WHERE id = :shift_id
            """),
            {"shift_id": str(shift["id"]), "duration": duration_seconds}
        )
    else:
        # Нет причины — обычная пауза
        db.execute(
            text("""
                UPDATE shifts
                SET pause_seconds = COALESCE(pause_seconds, 0) + :duration
                WHERE id = :shift_id
            """),
            {"shift_id": str(shift["id"]), "duration": duration_seconds}
        )
    
    db.commit()
    return PauseOut(**row)


@router.get("/pause/current")
def get_current_pause(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Получить текущую активную паузу (если есть)
    """
    shift = get_active_shift(db, current_user.id)
    if not shift:
        return {"pause": None, "on_pause": False}
    
    pause = get_active_pause(db, shift["id"])
    if pause:
        return {"pause": PauseOut(**pause), "on_pause": True}
    
    return {"pause": None, "on_pause": False}


@router.get("/pauses", response_model=List[PauseOut])
def get_shift_pauses(
    shift_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Получить все паузы смены
    """
    rows = db.execute(
        text("SELECT * FROM shift_pauses WHERE shift_id = :shift_id ORDER BY started_at"),
        {"shift_id": str(shift_id)}
    ).mappings().all()
    
    return [PauseOut(**r) for r in rows]


# ============================================================
# IDLE endpoints — простой (хранится как pause с idle-причиной)
# ============================================================

class StartIdleRequest(BaseModel):
    reason: str  # Причина простоя обязательна


@router.post("/idle/start", response_model=PauseOut)
def start_idle(
    payload: StartIdleRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Начать простой в текущей смене (хранится как пауза с причиной)
    """
    shift = get_active_shift(db, current_user.id)
    if not shift:
        raise HTTPException(status_code=400, detail="No active shift")

    existing_pause = get_active_pause(db, shift["id"], for_update=True)
    if existing_pause:
        raise HTTPException(status_code=400, detail="Already on pause/idle")

    row = db.execute(
        text("""
            INSERT INTO shift_pauses (shift_id, reason)
            VALUES (:shift_id, :reason)
            RETURNING *
        """),
        {"shift_id": str(shift["id"]), "reason": payload.reason}
    ).mappings().first()

    db.commit()

    # FIX(2026-05-04): push кураторам при простое.
    # FIX(2026-05-11) BELSI 2.0.0 build4: используем централизованный idle_push_routing
    # (брендбук ecosystem 05) — маршрутизация по ролям + объекту:
    #   - монтажник → бригадир + координатор объекта + куратор
    #   - работник → старший + начальник производства + куратор
    #   - водитель → логист + куратор
    try:
        from .idle_push_routing import notify_idle_event
        notify_idle_event(
            db=db,
            actor_user=current_user,
            reason_label=payload.reason,
            site_object_id=shift.get("site_object_id") if isinstance(shift, dict) else None,
        )
    except Exception as e:
        import logging
        logging.getLogger("idle").warning(f"idle push failed: {e}")

    return PauseOut(**row)


def _notify_curators_about_idle(db: Session, who: User, reason: str, shift: dict) -> None:
    """Push куратору/координатору когда монтажник нажал 'Простой'."""
    from .push_notifications import send_fcm_notification
    rows = db.execute(
        text("SELECT id, fcm_token FROM users WHERE role IN ('curator', 'coordinator', 'foreman') AND fcm_token IS NOT NULL")
    ).all()
    full_name = f"{who.first_name or ''} {who.last_name or ''}".strip() or who.phone
    title = f"⚠️ Простой: {full_name}"
    body = f"Причина: {reason}"
    for r in rows:
        send_fcm_notification(
            token=r.fcm_token,
            title=title,
            body=body,
            data={
                "type": "shift_idle_started",
                "user_id": str(who.id),
                "user_name": full_name,
                "user_phone": who.phone,
                "shift_id": str(shift["id"]),
                "reason": reason,
            },
        )


@router.post("/idle/end", response_model=PauseOut)
def end_idle(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """
    Завершить простой (аналогично завершению паузы)
    """
    shift = get_active_shift(db, current_user.id)
    if not shift:
        raise HTTPException(status_code=400, detail="No active shift")

    pause = get_active_pause(db, shift["id"], for_update=True)
    if not pause:
        # FIX(2026-05-06) HOTFIX 1.2.6 + 1.3.0: idempotent end_idle (см. end_pause).
        # Возвращаем синтетический закрытый объект с duration=0,
        # без записи в БД. Клиент onSuccess → state.isIdle=false.
        now = datetime.now(timezone.utc)
        return PauseOut(
            id=uuid4(),
            shift_id=shift["id"],
            started_at=now,
            ended_at=now,
            duration_seconds=0,
            reason=None,
        )

    started_at = pause["started_at"]
    ended_at = datetime.now(timezone.utc)
    duration_seconds = int((ended_at - started_at).total_seconds())

    row = db.execute(
        text("""
            UPDATE shift_pauses
            SET ended_at = :ended_at, duration_seconds = :duration
            WHERE id = :id
            RETURNING *
        """),
        {"id": str(pause["id"]), "ended_at": ended_at, "duration": duration_seconds}
    ).mappings().first()

    # Обновляем idle_seconds в смене
    db.execute(
        text("""
            UPDATE shifts
            SET idle_seconds = COALESCE(idle_seconds, 0) + :duration
            WHERE id = :shift_id
        """),
        {"shift_id": str(shift["id"]), "duration": duration_seconds}
    )

    db.commit()
    return PauseOut(**row)
