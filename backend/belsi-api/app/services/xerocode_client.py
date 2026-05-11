"""
XeroCode AI Gateway client для BELSI 1.3.0.

Клиент к https://xerocode.ru/api/v1/external/* — единственная точка вызова AI
из BELSI. Никогда не вызываем Gemini/Claude/Groq напрямую — всё через XeroCode.

Особенности:
- async httpx с таймаутом 30 сек (90 сек для transcribe — аудио тяжелее)
- 2 retry на сетевых сбоях с экспоненциальным backoff
- Feature-flag XEROCODE_ENABLED — при False все методы возвращают None
- Все методы возвращают `dict | None`. None = тихий сбой, caller игнорирует
- НЕ бросает исключения наружу (logger.warning + return None)
- по умолчанию `allow_paid_fallback=False` — защита от прожига баланса

Использование:
    from app.services.xerocode_client import xerocode_client

    result = await xerocode_client.analyze_image(
        image_url="https://api.belsi.ru/static/photos/abc.jpg",
        prompt_template="photo_quality",
        request_id=f"belsi-photo-{photo_id}",
    )
    if result is None:
        # XeroCode недоступен или отключён — пропускаем AI-анализ
        return
    # result["score"], result["comment"], result["category"], ...
"""
from __future__ import annotations

import asyncio
import logging
import os
from typing import Any, Optional

import httpx

logger = logging.getLogger("xerocode_client")


class XeroCodeClient:
    """
    Async client для XeroCode AI Gateway.

    Singleton — экземпляр создаётся один раз при импорте, не на каждый запрос.
    Конфигурация через env-переменные:
        XEROCODE_API_URL — base URL, по умолчанию https://xerocode.ru/api/v1/external
        XEROCODE_SERVICE_TOKEN — Bearer-токен от XeroCode admin
        XEROCODE_TIMEOUT_SEC — таймаут одного запроса (default 30)
        XEROCODE_ENABLED — feature flag, "false" вырубает все вызовы (default true)
    """

    DEFAULT_TIMEOUT = 30.0
    TRANSCRIBE_TIMEOUT = 90.0  # аудио может быть до 25 МБ, нужно больше
    MAX_RETRIES = 2

    def __init__(self) -> None:
        self.base_url = os.getenv("XEROCODE_API_URL", "https://xerocode.ru/api/v1/external").rstrip("/")
        self.token = os.getenv("XEROCODE_SERVICE_TOKEN", "")
        self.timeout = float(os.getenv("XEROCODE_TIMEOUT_SEC", str(self.DEFAULT_TIMEOUT)))
        self.enabled = os.getenv("XEROCODE_ENABLED", "true").lower() in ("true", "1", "yes")

        if self.enabled and not self.token:
            logger.warning(
                "XEROCODE_ENABLED=true but XEROCODE_SERVICE_TOKEN is empty — "
                "AI-вызовы будут падать в 401. Установите токен или выключите."
            )

    # ─────────────────────────────────────────────────────────────
    # Внутренний транспорт
    # ─────────────────────────────────────────────────────────────

    async def _post_json(
        self,
        path: str,
        payload: dict,
        timeout: float | None = None,
    ) -> Optional[dict]:
        """POST JSON с retry и тихим сбоем. Возвращает result из envelope или None."""
        if not self.enabled:
            logger.debug("XeroCode disabled, skipping %s", path)
            return None
        if not self.token:
            logger.warning("XeroCode: no token, skipping %s", path)
            return None

        url = f"{self.base_url}{path}"
        headers = {
            "Authorization": f"Bearer {self.token}",
            "Content-Type": "application/json",
        }
        actual_timeout = timeout or self.timeout

        for attempt in range(self.MAX_RETRIES + 1):
            try:
                async with httpx.AsyncClient(timeout=actual_timeout) as client:
                    response = await client.post(url, headers=headers, json=payload)

                # 401/403/404 — нет смысла ретраить, что-то с нами/конфигом
                if response.status_code in (401, 403, 404):
                    logger.error(
                        "XeroCode %s: %d %s — проверьте XEROCODE_SERVICE_TOKEN и allowed_endpoints",
                        path, response.status_code, response.text[:200],
                    )
                    return None

                # 429 / 5xx — retry
                if response.status_code >= 500 or response.status_code == 429:
                    if attempt < self.MAX_RETRIES:
                        backoff = 2 ** attempt
                        logger.info("XeroCode %s: HTTP %d, retry через %ds", path, response.status_code, backoff)
                        await asyncio.sleep(backoff)
                        continue
                    logger.warning("XeroCode %s: HTTP %d — отказались после %d retry", path, response.status_code, self.MAX_RETRIES)
                    return None

                if response.status_code != 200:
                    logger.warning("XeroCode %s: unexpected HTTP %d", path, response.status_code)
                    return None

                envelope = response.json()
                if not envelope.get("ok"):
                    error = envelope.get("error", {})
                    logger.warning(
                        "XeroCode %s returned ok=false: %s — %s",
                        path, error.get("code"), error.get("message"),
                    )
                    return None

                # Возвращаем result + meta для того кто захочет залогировать модель
                return {
                    "result": envelope.get("result", {}),
                    "meta": envelope.get("meta", {}),
                    "request_id": envelope.get("request_id"),
                }

            except (httpx.TimeoutException, httpx.NetworkError) as e:
                if attempt < self.MAX_RETRIES:
                    backoff = 2 ** attempt
                    logger.info("XeroCode %s: %s, retry через %ds", path, type(e).__name__, backoff)
                    await asyncio.sleep(backoff)
                    continue
                logger.warning("XeroCode %s недоступен: %s", path, e)
                return None
            except Exception as e:
                logger.exception("XeroCode %s неожиданная ошибка: %s", path, e)
                return None

        return None

    async def _post_multipart(
        self,
        path: str,
        files: dict,
        data: dict,
        timeout: float | None = None,
    ) -> Optional[dict]:
        """POST multipart/form-data (для transcribe). Логика та же что в _post_json."""
        if not self.enabled or not self.token:
            return None

        url = f"{self.base_url}{path}"
        headers = {"Authorization": f"Bearer {self.token}"}
        actual_timeout = timeout or self.TRANSCRIBE_TIMEOUT

        for attempt in range(self.MAX_RETRIES + 1):
            try:
                async with httpx.AsyncClient(timeout=actual_timeout) as client:
                    response = await client.post(url, headers=headers, files=files, data=data)

                if response.status_code in (401, 403, 404):
                    logger.error("XeroCode %s: %d", path, response.status_code)
                    return None

                if response.status_code >= 500 or response.status_code == 429:
                    if attempt < self.MAX_RETRIES:
                        await asyncio.sleep(2 ** attempt)
                        continue
                    return None

                if response.status_code != 200:
                    logger.warning("XeroCode %s: HTTP %d", path, response.status_code)
                    return None

                envelope = response.json()
                if not envelope.get("ok"):
                    return None

                return {
                    "result": envelope.get("result", {}),
                    "meta": envelope.get("meta", {}),
                    "request_id": envelope.get("request_id"),
                }
            except Exception as e:
                logger.warning("XeroCode %s multipart exception: %s", path, e)
                if attempt < self.MAX_RETRIES:
                    await asyncio.sleep(2 ** attempt)
                    continue
                return None

        return None

    # ─────────────────────────────────────────────────────────────
    # Публичные методы для каждого use case
    # ─────────────────────────────────────────────────────────────

    async def analyze_image(
        self,
        image_url: str | None = None,
        image_base64: str | None = None,
        prompt_template: str = "photo_quality",
        custom_context: dict | None = None,
        request_id: str | None = None,
        model_override: str | None = None,
        allow_paid_fallback: bool = False,
    ) -> Optional[dict]:
        """
        Vision-анализ изображения через XeroCode.
        Использовать для photo_quality / idle_verify.

        Возвращает {"result": {...}, "meta": {...}, "request_id": "..."} или None.
        """
        payload: dict[str, Any] = {
            "prompt_template": prompt_template,
            "allow_paid_fallback": allow_paid_fallback,
        }
        if image_url:
            payload["image_url"] = image_url
        if image_base64:
            payload["image_base64"] = image_base64
        if custom_context:
            payload["custom_context"] = custom_context
        if request_id:
            payload["request_id"] = request_id
        if model_override:
            payload["model_override"] = model_override

        return await self._post_json("/analyze-image", payload)

    async def generate(
        self,
        prompt_template: str,
        data: dict,
        request_id: str | None = None,
        model_override: str | None = None,
        allow_paid_fallback: bool = False,
    ) -> Optional[dict]:
        """
        Текстовая генерация: daily_summary / triage_ticket / chat_reply_suggest /
        query_to_filter / stock_forecast.
        """
        payload: dict[str, Any] = {
            "prompt_template": prompt_template,
            "data": data,
            "allow_paid_fallback": allow_paid_fallback,
        }
        if request_id:
            payload["request_id"] = request_id
        if model_override:
            payload["model_override"] = model_override

        return await self._post_json("/generate", payload)

    async def transcribe(
        self,
        audio_bytes: bytes,
        audio_filename: str = "audio.mp3",
        audio_mime: str = "audio/mpeg",
        language: str = "ru",
        prompt_hint: str | None = None,
        request_id: str | None = None,
        allow_paid_fallback: bool = False,
    ) -> Optional[dict]:
        """
        Транскрипция аудио через Groq Whisper (или OpenAI fallback).
        """
        files = {"audio": (audio_filename, audio_bytes, audio_mime)}
        data: dict[str, Any] = {
            "language": language,
            "allow_paid_fallback": str(allow_paid_fallback).lower(),
        }
        if prompt_hint:
            data["prompt_hint"] = prompt_hint
        if request_id:
            data["request_id"] = request_id

        return await self._post_multipart("/transcribe", files=files, data=data)

    async def usage(self) -> Optional[dict]:
        """Получить текущую квоту и расход."""
        if not self.enabled or not self.token:
            return None
        url = f"{self.base_url}/usage"
        headers = {"Authorization": f"Bearer {self.token}"}
        try:
            async with httpx.AsyncClient(timeout=10.0) as client:
                response = await client.get(url, headers=headers)
            if response.status_code == 200:
                return response.json()
        except Exception as e:
            logger.warning("XeroCode usage exception: %s", e)
        return None

    async def health(self) -> bool:
        """Smoke-проверка доступности."""
        if not self.enabled:
            return False
        url = f"{self.base_url}/health"
        try:
            async with httpx.AsyncClient(timeout=5.0) as client:
                response = await client.get(url)
            return response.status_code == 200
        except Exception:
            return False


# Singleton — один экземпляр на весь процесс
xerocode_client = XeroCodeClient()
