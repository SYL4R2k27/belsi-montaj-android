"""
Voice input endpoint для BELSI 1.3.0.

Принимает аудио от Android-клиента, шлёт в XeroCode → Groq Whisper,
возвращает транскрипцию + опциональную классификацию (через generate).

Используется в 3 местах в Android UI:
1. Голосовая причина простоя (idle reason) — после транскрипции опц.
   запускается query_to_filter для извлечения структурированной причины.
2. Голосовая заявка снабженцу (material_order)
3. Голосовой комментарий к фото (просто транскрипция)

Endpoint: POST /shift/voice/transcribe
"""
from __future__ import annotations

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4, UUID

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile
from pydantic import BaseModel
from sqlalchemy.orm import Session

from .auth import get_current_user
from .db import get_db
from .models import User, AiAnalysis

router = APIRouter(prefix="/shift", tags=["voice-input"])


class VoiceTranscribeResponse(BaseModel):
    text: str
    language_detected: Optional[str] = None
    duration_sec: Optional[float] = None
    request_id: str
    model_used: Optional[str] = None


@router.post("/voice/transcribe", response_model=VoiceTranscribeResponse)
async def transcribe(
    audio: UploadFile = File(...),
    language: str = Form("ru"),
    context: str = Form("general"),  # general / idle / material / photo_comment
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Транскрибирует аудио в текст через XeroCode → Groq Whisper.

    context влияет на prompt_hint для Whisper:
    - "idle" — подсказка что это причина простоя
    - "material" — подсказка про материалы и заявки
    - "photo_comment" — общий стройучёт
    - "general" — без контекста
    """
    from .services.xerocode_client import xerocode_client

    # Размер ограничение
    audio_bytes = await audio.read()
    if len(audio_bytes) > 25 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Аудио слишком большое (max 25MB)")
    if not audio_bytes:
        raise HTTPException(status_code=400, detail="Пустой файл")

    # Определяем MIME
    mime_map = {
        "mp3": "audio/mpeg", "wav": "audio/wav", "m4a": "audio/mp4",
        "webm": "audio/webm", "ogg": "audio/ogg", "flac": "audio/flac",
    }
    extension = (audio.filename or "audio.mp3").rsplit(".", 1)[-1].lower()
    mime = audio.content_type or mime_map.get(extension, "audio/mpeg")

    # Контекстные подсказки для Whisper
    prompt_hints = {
        "idle": "Причина простоя на стройке: нет розетки, нет материала, нет инструмента, ждём бригадира, не приехал снабженец.",
        "material": "Заявка на материалы для мебельной фабрики: ЛДСП, МДФ, кромка ПВХ, конфирмат, минификс, петля, направляющая, ручка.",
        "photo_comment": "Комментарий монтажника к фото: установлен, собран, готово, требуется доделка, проблема с фасадом.",
        "general": None,
    }
    prompt_hint = prompt_hints.get(context)

    request_id = f"belsi-voice-{uuid4()}"

    envelope = await xerocode_client.transcribe(
        audio_bytes=audio_bytes,
        audio_filename=audio.filename or "audio.mp3",
        audio_mime=mime,
        language=language,
        prompt_hint=prompt_hint,
        request_id=request_id,
        allow_paid_fallback=False,
    )

    if envelope is None:
        raise HTTPException(status_code=503, detail="Сервис распознавания временно недоступен")

    result = envelope.get("result", {}) or {}
    meta = envelope.get("meta", {}) or {}

    # Сохраняем в ai_analyses для аудита
    try:
        analysis = AiAnalysis(
            user_id=current_user.id,
            analysis_type="voice_transcribe",
            result_json=result,
            result_text=result.get("text"),
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

    return VoiceTranscribeResponse(
        text=result.get("text", ""),
        language_detected=result.get("language_detected"),
        duration_sec=result.get("duration_sec"),
        request_id=request_id,
        model_used=meta.get("model_used"),
    )
