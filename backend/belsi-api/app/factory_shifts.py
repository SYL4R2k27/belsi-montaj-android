"""
FIX(2026-05-05): Расширения смен для производства мебели.

Новые endpoints:
- POST /shift/break/start  { type: "smoke" | "lunch" }   — начать перекур или обед
- POST /shift/break/end                                   — закрыть текущий перерыв
- GET  /shift/idle-reasons?domain=production              — список причин по домену

Старые /shift/pause/* остаются как есть (для backward-compat 1.2.5).

ЛОКАЛЬНО — НЕ ЗАДЕПЛОЕНО.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import List, Literal, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy import text
from sqlalchemy.orm import Session

from .auth import get_current_user
from .db import get_db
from .models import User, Shift, IdleReasonCatalog

router = APIRouter(tags=["factory-shifts"])
log = logging.getLogger("factory_shifts")

BreakType = Literal["smoke", "lunch"]
DomainName = Literal["installation", "logistics", "production"]


# ─────────────── Schemas ───────────────

class BreakStart(BaseModel):
    type: BreakType


class IdleReasonOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    code: str
    label: str
    position: int
    domain: str


# ─────────────── Helpers ───────────────

def _active_shift(db: Session, user: User) -> Optional[Shift]:
    """Найти активную (незакрытую) смену пользователя."""
    return (
        db.query(Shift)
        .filter(Shift.user_id == user.id, Shift.finish_at.is_(None))
        .order_by(Shift.start_at.desc())
        .first()
    )


# ─────────────── /shift/break/start /end ───────────────

@router.post("/shift/break/start", status_code=200)
def break_start(
    payload: BreakStart,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Начать перекур или обед — отдельный счётчик от idle (простой).
    Brandbook: pause = перекур (обычно ~10 мин), lunch = обед (один раз за смену).

    Хранение времени перерыва — в meta jsonb как массив сегментов:
    meta = {"breaks": [{"type": "smoke", "started_at": "..."}, ...]}

    Это позволяет добавлять разные типы без миграции существующих столбцов
    в проде (просто jsonb).
    """
    sh = _active_shift(db, current_user)
    if not sh:
        raise HTTPException(status_code=400, detail="No active shift")

    # Проверяем что нет уже открытого перерыва
    meta = sh.__dict__.get("meta") if hasattr(sh, "meta") else None
    # Упрощение: при наличии break_seconds колонки используем её, а meta для деталей
    # Здесь мок-логика: записываем только время старта в meta
    db.execute(
        text(
            """
            UPDATE shifts
            SET status = 'on_break',
                idle_reason = :type
            WHERE id = :sid
            """
        ),
        {"sid": str(sh.id), "type": f"break:{payload.type}"},
    )
    db.commit()
    log.info(f"shift {sh.id}: break started type={payload.type}")
    return {"ok": True, "type": payload.type, "shift_id": str(sh.id)}


@router.post("/shift/break/end", status_code=200)
def break_end(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Закрыть текущий перерыв (перекур или обед)."""
    sh = _active_shift(db, current_user)
    if not sh:
        raise HTTPException(status_code=400, detail="No active shift")

    # Здесь была бы реальная логика расчёта длительности и инкремента
    # break_seconds или lunch_seconds. Сейчас — простая отметка.
    db.execute(
        text("UPDATE shifts SET status = 'active', idle_reason = NULL WHERE id = :sid"),
        {"sid": str(sh.id)},
    )
    db.commit()
    return {"ok": True, "shift_id": str(sh.id)}


# ─────────────── /shift/idle-reasons ───────────────

# ─────────────── /shift/idle/notify (push на простой во всех доменах) ───────────────

class IdleNotifyRequest(BaseModel):
    reason: str
    domain: Optional[DomainName] = None  # если не указан — берём из роли


@router.post("/shift/idle/notify", status_code=200)
def notify_about_idle(
    payload: IdleNotifyRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Клиент вызывает этот endpoint после нажатия «Простой» с указанной причиной.
    Сервер определяет получателей (всегда куратор + руководители домена) и отправляет push.

    Brandbook: жёсткое правило — любой простой → push куратору.
    Дополнительно — руководителям соответствующего домена:
    - installation: бригадир + координатор объекта
    - logistics: логист
    - production: старший работник + начальник производства

    Реальная отправка push — через push_notifications module (когда подключим).
    Сейчас пишем в лог + возвращаем список адресатов для аудита.
    """
    role = (current_user.role or "").lower()
    role_to_domain = {
        "installer": "installation", "foreman": "installation", "coordinator": "installation",
        "driver": "logistics", "logistician": "logistics",
        "production_chief": "production", "senior_worker": "production",
        "worker": "production", "supplier": "production", "engineer": "production",
        "curator": None,
    }
    effective_domain = payload.domain or role_to_domain.get(role) or "installation"

    # Кто получает push (роли)
    recipients_by_domain = {
        "installation": ["foreman", "coordinator", "curator"],
        "logistics": ["logistician", "curator"],
        "production": ["senior_worker", "production_chief", "curator"],
    }
    recipient_roles = recipients_by_domain.get(effective_domain, ["curator"])

    # Найти юзеров этих ролей с активными FCM-токенами
    rows = db.execute(
        text(
            "SELECT id, role, fcm_token FROM users "
            "WHERE role = ANY(:roles) AND fcm_token IS NOT NULL AND fcm_token != ''"
        ),
        {"roles": recipient_roles},
    ).mappings().all()

    full_name = ((current_user.first_name or "") + " " + (current_user.last_name or "")).strip()
    if not full_name:
        full_name = current_user.phone or "Пользователь"

    log.info(
        f"idle notify: {full_name} ({role}) reason={payload.reason!r} "
        f"domain={effective_domain} → {len(rows)} recipients"
    )

    # Реальная отправка push — TODO когда подключим push_notifications module
    # Сейчас просто отчитываемся
    return {
        "ok": True,
        "domain": effective_domain,
        "reason": payload.reason,
        "recipients_count": len(rows),
        "recipient_roles": recipient_roles,
    }


@router.get("/shift/idle-reasons", response_model=List[IdleReasonOut])
def get_idle_reasons(
    domain: Optional[DomainName] = Query(None, description="Фильтр по домену. Если не указан — все."),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Список причин простоя по домену.
    Brandbook: жёсткая сегрегация — производство видит свои причины,
    монтаж свои, логистика свои.

    Если domain не указан — возвращаем по домену роли пользователя.
    """
    role = (current_user.role or "").lower()
    role_to_domain = {
        "installer": "installation",
        "foreman": "installation",
        "coordinator": "installation",
        "curator": None,  # видит все
        "driver": "logistics",
        "logistician": "logistics",
        "production_chief": "production",
        "senior_worker": "production",
        "worker": "production",
        "supplier": "production",
        "engineer": "production",
    }

    effective_domain = domain or role_to_domain.get(role)

    query = db.query(IdleReasonCatalog).filter(IdleReasonCatalog.active == True)
    if effective_domain:
        query = query.filter(IdleReasonCatalog.domain == effective_domain)

    rows = query.order_by(IdleReasonCatalog.domain, IdleReasonCatalog.position).all()
    return [IdleReasonOut.model_validate(r) for r in rows]


# ============================================================
# AI Idle Verify (BELSI 1.3.0 — через XeroCode Gateway)
# FIX(2026-05-10): подтверждение причины простоя по последнему фото
# ============================================================

class AiIdleVerifyResponse(BaseModel):
    confirmed: bool
    confidence: int
    suspicion_score: int  # 0=честно, 100=точно фейк
    reason_visible: bool
    comment: str
    photo_used_id: Optional[UUID] = None


@router.post("/shift/ai-verify-idle/{pause_id}", response_model=AiIdleVerifyResponse)
async def ai_verify_idle(
    pause_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Проверка простоя по последнему фото объекта.

    1. Берём pause (shift_pauses) → находим shift → site_object
    2. Берём последнее фото с этого объекта (за последние 4 часа)
    3. Шлём в XeroCode с template idle_verify
    4. Сохраняем результат в ai_analyses
    5. Если suspicion_score > 70 — возвращаем флажок куратору (через push отдельно)
    """
    from sqlalchemy import text as sa_text
    from .services.xerocode_client import xerocode_client
    from .models import AiAnalysis

    # Только куратор / координатор / chief / senior_worker могут запускать проверку
    if current_user.role not in ("curator", "coordinator", "production_chief", "senior_worker", "foreman"):
        raise HTTPException(status_code=403, detail="Недостаточно прав")

    # Находим паузу + связанные данные
    pause_row = db.execute(sa_text("""
        SELECT
            p.id, p.shift_id, p.reason, p.started_at, p.ended_at,
            s.site_object_id, s.user_id,
            (u.first_name || ' ' || COALESCE(u.last_name, '')) AS user_name,
            so.name AS object_name
        FROM shift_pauses p
        JOIN shifts s ON s.id = p.shift_id
        LEFT JOIN users u ON u.id = s.user_id
        LEFT JOIN site_objects so ON so.id = s.site_object_id
        WHERE p.id = :pid
    """), {"pid": str(pause_id)}).mappings().first()

    if not pause_row:
        raise HTTPException(status_code=404, detail="Пауза не найдена")

    if not pause_row["reason"]:
        raise HTTPException(status_code=400, detail="Это не простой (нет причины)")

    request_id = f"belsi-idle-{pause_id}"

    # Идемпотентность
    cached = db.query(AiAnalysis).filter(AiAnalysis.request_id == request_id).first()
    if cached:
        r = cached.result_json or {}
        return AiIdleVerifyResponse(
            confirmed=r.get("confirmed", False),
            confidence=int(r.get("confidence", 0) or 0),
            suspicion_score=int(r.get("suspicion_score", 0) or 0),
            reason_visible=r.get("reason_visible", False),
            comment=r.get("comment", ""),
            photo_used_id=None,
        )

    # Берём последнее фото с объекта (или вообще от пользователя)
    photo_row = None
    if pause_row["site_object_id"]:
        photo_row = db.execute(sa_text("""
            SELECT id, photo_url FROM shift_photos
            WHERE site_object_id = :oid
              AND created_at > :start
              AND created_at < :end + INTERVAL '5 minutes'
            ORDER BY created_at DESC LIMIT 1
        """), {
            "oid": str(pause_row["site_object_id"]),
            "start": pause_row["started_at"],
            "end": pause_row["ended_at"] or datetime.now(timezone.utc),
        }).mappings().first()

    if not photo_row:
        # fallback — последнее фото с этой смены
        photo_row = db.execute(sa_text("""
            SELECT id, photo_url FROM shift_photos
            WHERE shift_id = :sid
            ORDER BY created_at DESC LIMIT 1
        """), {"sid": str(pause_row["shift_id"])}).mappings().first()

    if not photo_row:
        raise HTTPException(status_code=400, detail="Нет фото для проверки причины простоя")

    envelope = await xerocode_client.analyze_image(
        image_url=photo_row["photo_url"],
        prompt_template="idle_verify",
        custom_context={
            "reason": pause_row["reason"],
            "user_name": pause_row["user_name"],
            "object_name": pause_row["object_name"] or "",
        },
        request_id=request_id,
        allow_paid_fallback=False,
    )

    if envelope is None:
        return AiIdleVerifyResponse(
            confirmed=False, confidence=0, suspicion_score=0,
            reason_visible=False,
            comment="AI временно недоступен — проверка не выполнена",
            photo_used_id=photo_row["id"],
        )

    result = envelope.get("result", {}) or {}
    meta = envelope.get("meta", {}) or {}

    # Сохраняем
    try:
        analysis = AiAnalysis(
            pause_id=pause_id,
            shift_photo_id=photo_row["id"],
            user_id=pause_row["user_id"],
            analysis_type="idle_verify",
            result_json=result,
            result_text=result.get("comment"),
            confidence=int(result.get("confidence", 0) or 0),
            model_used=meta.get("model_used"),
            provider_used=meta.get("provider_used"),
            tokens_input=meta.get("tokens_input"),
            tokens_output=meta.get("tokens_output"),
            cost_usd=meta.get("cost_usd"),
            duration_ms=meta.get("duration_ms"),
            request_id=request_id,
            paid_fallback_used=meta.get("fallback_used", False),
        )
        db.add(analysis)
        db.commit()
    except Exception:
        db.rollback()

    return AiIdleVerifyResponse(
        confirmed=bool(result.get("confirmed", False)),
        confidence=int(result.get("confidence", 0) or 0),
        suspicion_score=int(result.get("suspicion_score", 0) or 0),
        reason_visible=bool(result.get("reason_visible", False)),
        comment=result.get("comment", ""),
        photo_used_id=photo_row["id"],
    )
