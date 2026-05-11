"""
Единый модуль аутентификации.
JWT-токены + обратная совместимость с demo-token (для плавной миграции).
"""
import os
from datetime import datetime, timedelta, timezone

import jwt
from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy import text as sa_text
from sqlalchemy.orm import Session

from .db import get_db
from .models import User
from .settings import settings

security = HTTPBearer(auto_error=False)


# ─── JWT helpers ───────────────────────────────────────────────

def create_jwt_token(phone: str) -> str:
    """Создать JWT токен для пользователя по номеру телефона."""
    payload = {
        "sub": phone,
        "iat": datetime.now(timezone.utc),
        "exp": datetime.now(timezone.utc) + timedelta(hours=settings.jwt_expire_hours),
    }
    return jwt.encode(payload, settings.jwt_secret, algorithm=settings.jwt_algorithm)


def decode_jwt_token(token: str) -> str:
    """Декодировать JWT и вернуть phone. Raises jwt.* exceptions."""
    payload = jwt.decode(token, settings.jwt_secret, algorithms=[settings.jwt_algorithm])
    phone = payload.get("sub")
    if not phone:
        raise ValueError("No 'sub' in JWT payload")
    return phone


# ─── FastAPI dependency ────────────────────────────────────────

def get_current_user(
    request: Request,
    credentials: HTTPAuthorizationCredentials = Depends(security),
    db: Session = Depends(get_db),
) -> User:
    if credentials is None or not credentials.credentials:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Missing token")

    token = credentials.credentials

    # 1) Обратная совместимость: demo-token-* — РАЗРЕШЁН только если ALLOW_DEMO_TOKEN=1
    #    В продакшене небезопасен (любой знающий phone получает доступ).
    if token.startswith("demo-token-"):
        if os.getenv("ALLOW_DEMO_TOKEN", "0") != "1":
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        phone = token[len("demo-token-"):]
        user = db.query(User).filter(User.phone == phone).first()
        if not user:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
        _touch_last_seen(user, request)
        return user

    # 2) JWT-токен
    try:
        phone = decode_jwt_token(token)
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Token expired")
    except (jwt.InvalidTokenError, ValueError):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid token")

    user = db.query(User).filter(User.phone == phone).first()
    if not user:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="User not found")
    _touch_last_seen(user, request)
    return user

# ─── last_seen helper ──────────────────────────────────────────

def _touch_last_seen(user: User, request=None) -> None:
    """Обновить users.last_seen + app_version из заголовков запроса.

    FIX(2026-04-30): отдельная короткая сессия через engine.begin() —
    не закрывает транзакцию endpoint'a (вызывается ДО endpoint'a).
    Throttle 30 сек через WHERE — атомарно, без race между воркерами.
    Никогда не валит запрос (любая ошибка проглатывается).

    Заголовки клиента (опциональные):
      X-App-Version  — semver "1.2.3"
      X-App-Build    — int versionCode "42"
      X-App-Platform — "android" | "ios"
    """
    try:
        ver = build = plat = None
        if request is not None:
            ver = request.headers.get("x-app-version")
            build_str = request.headers.get("x-app-build")
            try:
                build = int(build_str) if build_str else None
            except (TypeError, ValueError):
                build = None
            plat = request.headers.get("x-app-platform")

        from .db import engine
        with engine.begin() as conn:
            conn.execute(
                sa_text("""
                    UPDATE users
                       SET last_seen = NOW(),
                           app_version = COALESCE(:ver, app_version),
                           app_build   = COALESCE(:build, app_build),
                           app_platform= COALESCE(:plat, app_platform),
                           app_version_seen_at = CASE
                               WHEN :ver IS NOT NULL THEN NOW()
                               ELSE app_version_seen_at
                           END
                     WHERE id = :uid
                       AND (last_seen IS NULL OR last_seen < NOW() - INTERVAL '30 seconds')
                """),
                {"uid": str(user.id), "ver": ver, "build": build, "plat": plat},
            )
    except Exception:
        # Никогда не ронять авторизацию из-за last_seen
        pass

