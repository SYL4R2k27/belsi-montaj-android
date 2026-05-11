"""
AI-extras — endpoints для smart_reply, triage_ticket, stock_forecast.

Все вызовы идут через xerocode_client.

Endpoints:
- POST /messenger/ai-suggest-replies — подсказки ответа в чате
- POST /support/ai-triage/{ticket_id} — auto-категоризация тикета
- GET  /production/materials/ai-forecast — прогноз закончится материал
"""
from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone, timedelta
from typing import Optional
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel
from sqlalchemy import text as sa_text
from sqlalchemy.orm import Session

from .auth import get_current_user
from .db import get_db
from .models import User, AiAnalysis

logger = logging.getLogger("ai_extras")


# ============================================================
# Smart Reply (chat_reply_suggest) — подсказки ответа в чате
# ============================================================

router_messenger = APIRouter(prefix="/messenger", tags=["messenger-ai"])


class SmartReplyRequest(BaseModel):
    thread_id: UUID
    incoming_message_id: Optional[UUID] = None
    incoming_text: str  # текст последнего сообщения от собеседника
    sender_name: str
    sender_role: str
    chat_history: Optional[list[dict]] = None  # [{role, name, text}, ...]


class SmartReplyResponse(BaseModel):
    replies: list[dict]  # [{"text": "...", "tone": "agree|info|delay|reject"}]
    context_understood: bool
    request_id: str


@router_messenger.post("/ai-suggest-replies", response_model=SmartReplyResponse)
async def smart_reply(
    payload: SmartReplyRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Подсказки 3 коротких ответов в чате через chat_reply_suggest.
    """
    from .services.xerocode_client import xerocode_client

    history_str = ""
    if payload.chat_history:
        for msg in payload.chat_history[-5:]:
            history_str += f"{msg.get('name', '?')} ({msg.get('role', '?')}): {msg.get('text', '')}\n"

    request_id = f"belsi-reply-{payload.incoming_message_id or uuid4()}"

    # FIX(2026-05-11) BELSI 2.0.0 (B1): idempotency через AiAnalysis.
    # Один и тот же incoming_message не должен дёргать AI повторно — экономим
    # квоту и обеспечиваем стабильность подсказок при reopen чата.
    cached = db.query(AiAnalysis).filter(AiAnalysis.request_id == request_id).first()
    if cached:
        r = cached.result_json or {}
        return SmartReplyResponse(
            replies=r.get("replies", []),
            context_understood=r.get("context_understood", True),
            request_id=request_id,
        )

    envelope = await xerocode_client.generate(
        prompt_template="chat_reply_suggest",
        data={
            "chat_history": history_str or "(история пустая)",
            "incoming_message": payload.incoming_text,
            "sender_name": payload.sender_name,
            "sender_role": payload.sender_role,
            "responder_role": current_user.role,
        },
        request_id=request_id,
        allow_paid_fallback=False,
    )

    if envelope is None:
        # Fallback на стандартные шаблоны — graceful degradation,
        # пользователь не видит ошибку, просто получает базовые подсказки.
        return SmartReplyResponse(
            replies=[
                {"text": "Понял", "tone": "agree"},
                {"text": "Уточню и отвечу", "tone": "delay"},
                {"text": "Пришлите подробнее", "tone": "info"},
            ],
            context_understood=False,
            request_id=request_id,
        )

    result = envelope.get("result", {}) or {}
    meta = envelope.get("meta", {}) or {}

    # FIX(2026-05-11) BELSI 2.0.0 (B1): пишем результат в ai_analyses для idempotency.
    try:
        analysis = AiAnalysis(
            user_id=current_user.id,
            analysis_type="smart_reply",
            result_json=result,
            result_text=None,
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

    return SmartReplyResponse(
        replies=result.get("replies", []),
        context_understood=result.get("context_understood", True),
        request_id=request_id,
    )


# ============================================================
# Triage support тикетов (триггерится фоном при INSERT тикета)
# ============================================================

router_support = APIRouter(prefix="/support", tags=["support-ai"])


class TriageResponse(BaseModel):
    ticket_id: UUID
    category: str
    subcategory: str
    priority: str
    tags: list[str]
    sentiment: str
    summary_short: str
    blocking: bool
    suggested_assignee_role: str
    request_id: str


@router_support.post("/ai-triage/{ticket_id}", response_model=TriageResponse)
async def triage_ticket(
    ticket_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Auto-классификация support тикета.
    Может вызываться вручную куратором или автоматически из support_chat
    после INSERT нового тикета.
    """
    from .services.xerocode_client import xerocode_client

    # Только куратор может триаджить
    if current_user.role not in ("curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Только куратор/координатор")

    # Берём последнее сообщение тикета
    ticket = db.execute(sa_text("""
        SELECT t.id, t.user_id,
               (u.first_name || ' ' || COALESCE(u.last_name, '')) AS user_name,
               u.role AS user_role,
               u.app_version
        FROM support_tickets t
        LEFT JOIN users u ON u.id = t.user_id
        WHERE t.id = :tid
    """), {"tid": str(ticket_id)}).mappings().first()

    if not ticket:
        raise HTTPException(status_code=404, detail="Тикет не найден")

    last_msg = db.execute(sa_text("""
        SELECT text FROM support_messages
        WHERE ticket_id = :tid AND sender_role = 'user'
        ORDER BY created_at DESC LIMIT 1
    """), {"tid": str(ticket_id)}).mappings().first()

    if not last_msg:
        raise HTTPException(status_code=400, detail="Нет сообщений от пользователя")

    request_id = f"belsi-triage-{ticket_id}"

    # Idempotency
    cached = db.query(AiAnalysis).filter(AiAnalysis.request_id == request_id).first()
    if cached:
        r = cached.result_json or {}
        return TriageResponse(
            ticket_id=ticket_id,
            category=r.get("category", "question"),
            subcategory=r.get("subcategory", "other"),
            priority=r.get("priority", "normal"),
            tags=r.get("tags", []),
            sentiment=r.get("sentiment", "neutral"),
            summary_short=r.get("summary_short", ""),
            blocking=r.get("blocking", False),
            suggested_assignee_role=r.get("suggested_assignee_role", "support"),
            request_id=request_id,
        )

    envelope = await xerocode_client.generate(
        prompt_template="triage_ticket",
        data={
            "user_name": ticket["user_name"] or "Пользователь",
            "user_role": ticket["user_role"] or "installer",
            "app_version": ticket["app_version"] or "?",
            "ticket_text": last_msg["text"],
        },
        request_id=request_id,
        allow_paid_fallback=False,
    )

    if envelope is None:
        # FIX(2026-05-11) BELSI 2.0.0 (B2): graceful fallback вместо HTTP 503.
        # Фронту лучше получить «normal»-приоритет с пометкой что AI недоступен,
        # чем ошибку — иначе TriageBadge на UI не покажется и куратор не увидит
        # тикет в списке (lazy load возвращает HTTP 5xx → бейдж скрыт).
        # Не пишем в ai_analyses — пусть следующий вызов попробует снова.
        return TriageResponse(
            ticket_id=ticket_id,
            category="question",
            subcategory="other",
            priority="normal",
            tags=[],
            sentiment="neutral",
            summary_short="(AI недоступен — приоритет по умолчанию)",
            blocking=False,
            suggested_assignee_role="support",
            request_id=request_id,
        )

    result = envelope.get("result", {}) or {}
    meta = envelope.get("meta", {}) or {}

    # Сохраняем
    try:
        analysis = AiAnalysis(
            user_id=ticket["user_id"],
            analysis_type="triage_ticket",
            result_json=result,
            result_text=result.get("summary_short"),
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

    return TriageResponse(
        ticket_id=ticket_id,
        category=result.get("category", "question"),
        subcategory=result.get("subcategory", "other"),
        priority=result.get("priority", "normal"),
        tags=result.get("tags", []),
        sentiment=result.get("sentiment", "neutral"),
        summary_short=result.get("summary_short", ""),
        blocking=result.get("blocking", False),
        suggested_assignee_role=result.get("suggested_assignee_role", "support"),
        request_id=request_id,
    )


# ============================================================
# Stock forecast — прогноз исчерпания материалов
# ============================================================

router_materials = APIRouter(prefix="/production/materials", tags=["materials-ai"])


class StockForecastItem(BaseModel):
    material_code: str
    material_name: str
    current_stock: int
    unit: str
    daily_avg_usage: float
    days_left: int
    risk: str  # critical / high / medium / low
    reason_ru: str
    recommended_order_quantity: int


class StockForecastResponse(BaseModel):
    forecasts: list[StockForecastItem]
    summary_ru: str
    actions: list[str]
    request_id: str


@router_materials.get("/ai-forecast", response_model=StockForecastResponse)
async def stock_forecast(
    facility_id: UUID = Query(...),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Прогноз исчерпания материалов для фабрики.
    Кэш — 6 часов (расход меняется не каждые 5 минут).
    """
    from .services.xerocode_client import xerocode_client

    if current_user.role not in ("supplier", "production_chief", "curator", "coordinator"):
        raise HTTPException(status_code=403, detail="Недостаточно прав")

    # Имя фабрики
    facility = db.execute(
        sa_text("SELECT name FROM site_objects WHERE id = :fid"),
        {"fid": str(facility_id)},
    ).mappings().first()
    if not facility:
        raise HTTPException(status_code=404, detail="Фабрика не найдена")

    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    request_id = f"belsi-forecast-{facility_id}-{today}"

    # Кэш 6 часов
    cached = db.query(AiAnalysis).filter(AiAnalysis.request_id == request_id).first()
    if cached and (datetime.now(timezone.utc) - cached.created_at) < timedelta(hours=6):
        r = cached.result_json or {}
        return StockForecastResponse(
            forecasts=[StockForecastItem(**f) for f in r.get("forecasts", [])],
            summary_ru=r.get("summary_ru", ""),
            actions=r.get("actions", []),
            request_id=request_id,
        )

    # Текущий остаток
    stock = db.execute(sa_text("""
        SELECT c.code, c.name, c.unit, c.min_stock,
               COALESCE(i.quantity, 0) AS quantity
        FROM materials_catalog c
        LEFT JOIN materials_inventory i ON i.material_id = c.id AND i.facility_id = :fid
        WHERE c.active = TRUE
        ORDER BY (COALESCE(i.quantity, 0) - c.min_stock) ASC
        LIMIT 20
    """), {"fid": str(facility_id)}).mappings().all()

    # История расхода — нет таблицы расхода в текущей схеме, делаем эвристику
    # на основе material_orders.delivered за 30 дней
    usage_history = db.execute(sa_text("""
        SELECT
            DATE(o.created_at) AS day,
            c.code,
            SUM(o.quantity_delivered) AS used
        FROM material_orders o
        JOIN materials_catalog c ON c.id = o.material_id
        WHERE o.facility_id = :fid
          AND o.created_at > NOW() - INTERVAL '30 days'
          AND o.status = 'delivered'
        GROUP BY DATE(o.created_at), c.code
        ORDER BY day DESC
    """), {"fid": str(facility_id)}).mappings().all()

    # Активные партии
    active_batches = db.execute(sa_text("""
        SELECT title, status, item_count
        FROM production_batches
        WHERE source_facility_id = :fid
          AND status IN ('draft', 'in_production')
        LIMIT 20
    """), {"fid": str(facility_id)}).mappings().all()

    data_for_ai = {
        "facility_name": facility["name"],
        "stock_data_json": [dict(r) for r in stock[:15]],
        "usage_history_json": [dict(r) for r in usage_history[:50]],
        "active_batches_json": [dict(r) for r in active_batches],
    }

    envelope = await xerocode_client.generate(
        prompt_template="stock_forecast",
        data=data_for_ai,
        request_id=request_id,
        allow_paid_fallback=False,
    )

    if envelope is None:
        # Fallback: считаем сами без AI
        forecasts = []
        for r in stock:
            if r["quantity"] is None:
                continue
            qty = int(r["quantity"])
            min_s = int(r["min_stock"] or 0)
            if qty < min_s:
                forecasts.append(StockForecastItem(
                    material_code=r["code"],
                    material_name=r["name"],
                    current_stock=qty,
                    unit=r["unit"],
                    daily_avg_usage=0.0,
                    days_left=0,
                    risk="critical",
                    reason_ru=f"Текущий остаток {qty} ниже минимума {min_s}",
                    recommended_order_quantity=max(min_s * 2, 10),
                ))
        return StockForecastResponse(
            forecasts=forecasts,
            summary_ru=f"AI-прогноз недоступен. {len(forecasts)} материалов ниже минимума.",
            actions=["Проверить XEROCODE_ENABLED в конфиге"],
            request_id=request_id,
        )

    result = envelope.get("result", {}) or {}
    meta = envelope.get("meta", {}) or {}

    try:
        analysis = AiAnalysis(
            user_id=current_user.id,
            analysis_type="stock_forecast",
            result_json=result,
            result_text=result.get("summary_ru"),
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

    forecasts = []
    for f in result.get("forecasts", []):
        try:
            forecasts.append(StockForecastItem(**f))
        except Exception:
            continue

    return StockForecastResponse(
        forecasts=forecasts,
        summary_ru=result.get("summary_ru", ""),
        actions=result.get("actions", []),
        request_id=request_id,
    )
