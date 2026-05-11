"""
FIX(2026-05-11) BELSI 2.0.0 (L3): rate-limiter для AI endpoints.

Цель — не убить квоту XeroCode при случайном или злонамеренном
шквале запросов с одного юзера. XeroCode-side уже имеет свой лимит
(60 RPM на service-account), но он гранулярности «весь BELSI». Этот
middleware ограничивает per-user_id, что снижает blast radius
одного юзера и даёт лучший error message клиенту.

Реализация — простой in-memory sliding window. Для масштабирования
на несколько worker-процессов и реального DDoS-защиты нужно
переезжать на Redis-based limiter (slowapi + redis). Пока для
1 worker / 14 юзеров — этого хватает.

Применение: к AI-endpoints через FastAPI dependency.

    from .ai_rate_limit import ai_rate_limit

    @router.post("/ai-..", dependencies=[Depends(ai_rate_limit)])
    async def my_ai_endpoint(...):
        ...
"""
from __future__ import annotations

import time
import threading
from collections import deque
from typing import Optional

from fastapi import Depends, HTTPException, Request

from .auth import get_current_user
from .models import User


# Лимиты per-user_id для AI endpoints (общий счётчик на все AI вызовы).
# Подобрано так, чтобы реальный куратор / монтажник не упирался:
# куратор смотрит ~30 фото в час (photo_quality в фоне) + 5 daily-summary
# + 5 triage. Лимит 60 в минуту = с двойным запасом.
AI_LIMIT_PER_MIN = 60
AI_WINDOW_SECONDS = 60

# Hot dict: user_id (str) -> deque[float] timestamps
_buckets: dict[str, deque[float]] = {}
_lock = threading.Lock()


def _is_over_limit(user_id: str) -> tuple[bool, int]:
    """
    Возвращает (over_limit, retry_after_seconds).
    Чистит старые timestamps и добавляет текущий, если не over.
    """
    now = time.monotonic()
    cutoff = now - AI_WINDOW_SECONDS
    with _lock:
        bucket = _buckets.setdefault(user_id, deque())
        # Чистим старые
        while bucket and bucket[0] < cutoff:
            bucket.popleft()
        if len(bucket) >= AI_LIMIT_PER_MIN:
            # До освобождения слота — секунды до того как самая старая запись выпадет за окно
            retry_after = int(bucket[0] + AI_WINDOW_SECONDS - now) + 1
            return True, max(1, retry_after)
        bucket.append(now)
        return False, 0


async def ai_rate_limit(
    request: Request,
    current_user: User = Depends(get_current_user),
):
    """
    FastAPI dependency для AI-endpoints. Возвращает HTTP 429 с
    Retry-After заголовком при превышении лимита.

    Не блокирует non-AI endpoints — там нет смысла, и они дешёвые.
    """
    over, retry_after = _is_over_limit(str(current_user.id))
    if over:
        raise HTTPException(
            status_code=429,
            detail=(
                f"Слишком много AI-запросов от вас. "
                f"Подождите {retry_after} сек и попробуйте снова."
            ),
            headers={"Retry-After": str(retry_after)},
        )
