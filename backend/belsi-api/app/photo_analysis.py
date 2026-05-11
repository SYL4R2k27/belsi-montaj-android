"""
AI-анализ качества фотографий смены.

Анализирует:
- Размытость (через вариацию яркости в блоках)
- Яркость (слишком тёмное / слишком светлое)
- Размер изображения (слишком маленькое)
- Контраст

Результат: текстовый комментарий + числовой score (0-100) + категория.
Фото НЕ отклоняется автоматически — всегда идёт на модерацию.

Зависимости: Pillow (pip install Pillow)
"""
import io
import logging
from datetime import datetime, timezone
from typing import Optional, Tuple
from uuid import UUID

import httpx
from PIL import Image, ImageStat, ImageFilter

from sqlalchemy.orm import Session
from .db import SessionLocal
from .models import ShiftPhoto

logger = logging.getLogger("photo_analysis")


def analyze_photo_quality(image_bytes: bytes) -> Tuple[Optional[str], int, str]:
    """
    Анализирует качество фото из байтов.
    Возвращает: (текстовый комментарий, score 0-100, категория).
    Категории: good, blur, dark, bright, low_contrast, low_res, unreadable
    """
    try:
        img = Image.open(io.BytesIO(image_bytes))
    except Exception:
        return "⚠️ Не удалось открыть изображение", 0, "unreadable"

    issues = []
    penalties = []  # (penalty_points, category)

    # 1. Проверка размера
    width, height = img.size
    if width < 320 or height < 240:
        issues.append("📐 Слишком маленькое разрешение")
        penalties.append((30, "low_res"))
    elif width < 640 or height < 480:
        issues.append("📐 Низкое разрешение")
        penalties.append((10, "low_res"))

    # 2. Конвертируем в grayscale для анализа
    gray = img.convert("L")
    stat = ImageStat.Stat(gray)

    # 3. Проверка яркости (mean brightness)
    mean_brightness = stat.mean[0]
    if mean_brightness < 25:
        issues.append("🌑 Фото очень тёмное")
        penalties.append((35, "dark"))
    elif mean_brightness < 40:
        issues.append("🌑 Фото слишком тёмное")
        penalties.append((20, "dark"))
    elif mean_brightness > 240:
        issues.append("☀️ Фото сильно засвечено")
        penalties.append((30, "bright"))
    elif mean_brightness > 235:
        issues.append("☀️ Фото слишком яркое / засвечено")
        penalties.append((15, "bright"))

    # 4. Проверка контраста (стандартное отклонение яркости)
    std_dev = stat.stddev[0]
    if std_dev < 10:
        issues.append("🔲 Очень низкий контраст")
        penalties.append((25, "low_contrast"))
    elif std_dev < 15:
        issues.append("🔲 Низкий контраст")
        penalties.append((15, "low_contrast"))

    # 5. Проверка размытости (Laplacian через edge detection)
    edges = gray.filter(ImageFilter.FIND_EDGES)
    edge_stat = ImageStat.Stat(edges)
    edge_variance = edge_stat.var[0]
    if edge_variance < 20:
        issues.append("🔍 Фото сильно размыто")
        penalties.append((35, "blur"))
    elif edge_variance < 50:
        issues.append("🔍 Фото размыто")
        penalties.append((20, "blur"))

    # Рассчитываем score
    total_penalty = sum(p[0] for p in penalties)
    score = max(0, min(100, 100 - total_penalty))

    # Определяем главную категорию (наибольший штраф)
    if not penalties:
        category = "good"
        comment = "✅ Фото проверено, проблем не выявлено"
    else:
        # Сортируем по штрафу, берём самый большой
        penalties.sort(key=lambda x: x[0], reverse=True)
        category = penalties[0][1]
        comment = " · ".join(issues)

    return comment, score, category


async def analyze_photo_from_url(photo_url: str) -> Tuple[Optional[str], int, str]:
    """
    Скачивает фото по URL и анализирует качество.
    """
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.get(photo_url)
            if response.status_code != 200:
                logger.warning(f"Failed to download photo: {photo_url}, status={response.status_code}")
                return None, 0, "unreadable"
            return analyze_photo_quality(response.content)
    except Exception as e:
        logger.error(f"Error downloading/analyzing photo: {e}")
        return None, 0, "unreadable"


def analyze_photo_sync(photo_url: str) -> Tuple[Optional[str], int, str]:
    """
    Синхронная версия — скачивает фото и анализирует.
    Используется в BackgroundTask (FastAPI).
    """
    import httpx as _httpx
    try:
        with _httpx.Client(timeout=30.0) as client:
            response = client.get(photo_url)
            if response.status_code != 200:
                logger.warning(f"Failed to download photo: {photo_url}, status={response.status_code}")
                return None, 0, "unreadable"
            return analyze_photo_quality(response.content)
    except Exception as e:
        logger.error(f"Error downloading/analyzing photo: {e}")
        return None, 0, "unreadable"


def _get_auto_approve_threshold(db: Session) -> int:
    """Получить порог автоодобрения из app_settings. 0 = выключено."""
    try:
        from sqlalchemy import text as sa_text
        row = db.execute(sa_text("SELECT value FROM app_settings WHERE key = 'auto_approve_threshold'")).first()
        return int(row[0]) if row else 0
    except Exception:
        return 0


def run_photo_analysis(photo_id: str, photo_url: str):
    """
    Фоновая задача: анализирует фото и записывает результат в БД.
    Автоодобрение если score >= порог (настраивается в app_settings).

    FIX(2026-05-10) BELSI 1.3.0: AI-анализ через XeroCode Gateway (Gemini Flash).
    Если XEROCODE_ENABLED=true и токен настроен — используем XeroCode для
    качественного семантического анализа (каска / селфи / стена / процесс).
    Иначе — fallback на старый локальный analyze_photo_sync (Pillow-based).

    Старый Pillow-анализ остался как safety-net: если XeroCode недоступен
    (timeout / 5xx / отключён) — фото не остаётся без комментария.
    """
    db: Session = SessionLocal()
    try:
        photo = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
        if not photo:
            logger.warning(f"Photo {photo_id} not found for analysis")
            return

        # Сначала пробуем XeroCode AI Gateway (если включён)
        xerocode_result = None
        try:
            xerocode_result = _analyze_via_xerocode(photo_id, photo_url, db)
        except Exception as e:
            logger.warning(f"XeroCode analysis exception for {photo_id}: {e}")
            xerocode_result = None

        if xerocode_result is not None:
            # Используем результат от Gemini/XeroCode
            ai_comment = xerocode_result["comment"]
            ai_score = xerocode_result["score_100"]   # 0-100 для совместимости
            ai_category = xerocode_result["category"]
            logger.info(f"Photo {photo_id}: XeroCode → score={ai_score}, cat={ai_category}")
        else:
            # Fallback на старый локальный анализ
            ai_comment, ai_score, ai_category = analyze_photo_sync(photo_url)
            logger.info(f"Photo {photo_id}: local fallback → score={ai_score}, cat={ai_category}")

        photo.ai_comment = ai_comment
        photo.ai_score = ai_score
        photo.ai_category = ai_category
        photo.ai_analyzed_at = datetime.now(timezone.utc)

        # Автоодобрение по порогу
        threshold = _get_auto_approve_threshold(db)
        if threshold > 0 and ai_score >= threshold and photo.status == "pending":
            photo.status = "approved"
            logger.info(f"Photo {photo_id} AUTO-APPROVED: score={ai_score} >= threshold={threshold}")

        db.commit()

        # Push координатору при проблемном фото (score < 60)
        if ai_score < 60:
            _notify_coordinator_about_problem_photo(db, photo, ai_comment)
    except Exception as e:
        logger.error(f"Photo analysis failed for {photo_id}: {e}")
    finally:
        db.close()


def _analyze_via_xerocode(photo_id: str, photo_url: str, db: Session) -> dict | None:
    """
    Вызов XeroCode AI Gateway (sync wrapper для async-клиента).

    Возвращает dict {"comment": str, "score_100": int, "category": str}
    или None если XeroCode недоступен.

    Также сохраняет ПОЛНЫЙ JSON-ответ в `ai_analyses` для аудита.

    FIX(2026-05-10) v1.3.2: передаём пользовательский comment как контекст
    в custom_context. Это критично для оценки — например фото повреждённой
    петли с комментом "проблема с фурнитурой" должно интерпретироваться
    как documentation (фото-доказательство), а не как plain workplace 5/10.
    """
    import asyncio
    import json
    from .services.xerocode_client import xerocode_client
    from .models import AiAnalysis, ShiftPhoto

    if not xerocode_client.enabled:
        return None

    request_id = f"belsi-photo-{photo_id}"

    # Идемпотентность: если уже анализировали — возвращаем кэш из БД
    existing = db.query(AiAnalysis).filter(AiAnalysis.request_id == request_id).first()
    if existing:
        result = existing.result_json or {}
        return {
            "comment": existing.result_text or result.get("comment", ""),
            "score_100": int((result.get("score", 5) or 5) * 10),
            "category": result.get("category", "unknown"),
        }

    # FIX(2026-05-10) v1.3.2: подтягиваем comment монтажника как контекст для AI.
    # Если монтажник написал "готовый шкаф" — AI правильно интерпретирует фото
    # как result, а не как "пустое рабочее место без работника".
    user_comment = ""
    photo_row = db.query(ShiftPhoto).filter(ShiftPhoto.id == photo_id).first()
    if photo_row and photo_row.comment:
        user_comment = photo_row.comment.strip()[:500]  # ограничение на длину промпта

    custom_context = {"user_comment": user_comment} if user_comment else None

    # Async-вызов из sync-контекста (run_photo_analysis вызывается из BackgroundTasks)
    try:
        loop = asyncio.new_event_loop()
        asyncio.set_event_loop(loop)
        envelope = loop.run_until_complete(
            xerocode_client.analyze_image(
                image_url=photo_url,
                prompt_template="photo_quality",
                custom_context=custom_context,
                request_id=request_id,
                allow_paid_fallback=False,  # экономим бюджет — фото-анализ масштабный
            )
        )
        loop.close()
    except Exception as e:
        logger.warning(f"XeroCode async call failed: {e}")
        return None

    if envelope is None:
        return None

    result = envelope.get("result", {}) or {}
    meta = envelope.get("meta", {}) or {}

    # Score AI: 0-10. Конвертируем в 0-100 для совместимости со старым кодом.
    ai_score_010 = result.get("score", 5) or 5
    score_100 = int(ai_score_010 * 10)

    # Сохраняем ПОЛНЫЙ JSON в ai_analyses
    try:
        analysis = AiAnalysis(
            shift_photo_id=photo_id,
            analysis_type="photo_quality",
            result_json=result,
            result_text=result.get("comment"),
            confidence=int(ai_score_010 * 10),
            model_used=meta.get("model_used"),
            provider_used=meta.get("provider_used"),
            tokens_input=meta.get("tokens_input"),
            tokens_output=meta.get("tokens_output"),
            cost_usd=meta.get("cost_usd"),
            duration_ms=meta.get("duration_ms"),
            request_id=request_id,
            paid_fallback_used=meta.get("fallback_used", False) and meta.get("provider_used") in ("anthropic", "openai", "apiyi"),
        )
        db.add(analysis)
        # commit будет в caller
    except Exception as e:
        logger.warning(f"Failed to insert ai_analyses: {e}")

    # FIX(2026-05-10) v1.3.1: разделяем issues (⚠ критичные) и info (· нейтральные).
    # photo_quality v1.3.1 теперь возвращает 2 списка вместо одного:
    # - issues: ТОЛЬКО критичные нарушения (селфи / размыто / без каски / курение)
    # - info:   нейтральные наблюдения ("без работника", "после работы")
    # Раньше "отсутствие работника" падало в issues с эмодзи ⚠ — это мешало куратору.
    comment_parts = [result.get("comment", "")]
    issues = result.get("issues") or []
    info = result.get("info") or []
    if issues:
        comment_parts.append("⚠ " + ", ".join(issues))
    if info:
        comment_parts.append("· " + ", ".join(info))

    return {
        "comment": " · ".join(filter(None, comment_parts))[:500],
        "score_100": score_100,
        "category": result.get("category", "unknown"),
    }


def _notify_coordinator_about_problem_photo(db: Session, photo, ai_comment: str):
    """
    Находит координатора объекта и отправляет ему push-уведомление.
    """
    try:
        from .models import Shift, SiteObject, User

        shift = db.query(Shift).filter(Shift.id == photo.shift_id).first()
        if not shift:
            return

        site_object_id = getattr(shift, "site_object_id", None)
        if not site_object_id:
            return

        site_obj = db.query(SiteObject).filter(SiteObject.id == site_object_id).first()
        if not site_obj or not site_obj.coordinator_id:
            return

        coordinator = db.query(User).filter(User.id == site_obj.coordinator_id).first()
        if not coordinator:
            return

        try:
            from sqlalchemy import text as sa_text
            from .push_notifications import send_fcm_notification

            installer = db.query(User).filter(User.id == shift.user_id).first()
            installer_name = installer.full_name if installer else "Монтажник"

            row = db.execute(
                sa_text("SELECT fcm_token FROM users WHERE id = :id"),
                {"id": str(coordinator.id)}
            ).first()

            if row and row.fcm_token:
                send_fcm_notification(
                    token=row.fcm_token,
                    title="⚠️ Проблемное фото",
                    body=f"{installer_name}: {ai_comment}",
                    data={
                        "type": "problem_photo",
                        "photo_id": str(photo.id),
                        "ai_comment": ai_comment,
                    },
                )
            logger.info(f"Push sent to coordinator {coordinator.id} about problem photo {photo.id}")
        except Exception as push_err:
            logger.warning(f"Failed to send push to coordinator: {push_err}")

    except Exception as e:
        logger.warning(f"Error notifying coordinator: {e}")
