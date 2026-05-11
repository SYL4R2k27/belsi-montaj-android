"""Version policy endpoint.

GET /version  — отдаёт min_supported, recommended, latest для Android.

Идея 2-недельного безопасного перехода:
  1. Сразу после релиза 1.1.0 — min_supported = 4 (то же что 1.1.0).
     **НЕ выставляем выше**, чтобы старые APK работали 2 недели.
  2. Recommended = 4 (1.1.0): клиент покажет баннер "доступно обновление".
  3. Через 2 недели меняем min_supported на 4 → старые APK получат блокировку.

Хранится в app_settings (key/value).
"""
from fastapi import APIRouter, Depends, Request
from sqlalchemy.orm import Session
from sqlalchemy import text as sa_text

from .db import get_db

router = APIRouter(tags=["version"])

# По умолчанию (если в app_settings ничего нет):
DEFAULT_LATEST_BUILD = 4         # versionCode APK 1.1.0
DEFAULT_LATEST_NAME  = "1.1.0"
DEFAULT_MIN_BUILD    = 3         # старая 1.0.2 ещё работает (2 недели)
DEFAULT_RECOMMENDED  = 4         # рекомендуем 1.1.0


def _get_setting(db: Session, key: str) -> str | None:
    row = db.execute(
        sa_text("SELECT value FROM app_settings WHERE key = :k"),
        {"k": key},
    ).first()
    return row[0] if row else None


@router.get("/version")
def get_version_policy(
    request: Request,
    db: Session = Depends(get_db),
):
    """Возвращает версионную политику.

    Клиент сам решает:
      - если build < min_supported → force update
      - если build < recommended   → soft banner "обновитесь"
      - download_url → ссылка на APK
    """
    try:
        min_b = int(_get_setting(db, "min_app_build") or DEFAULT_MIN_BUILD)
    except (TypeError, ValueError):
        min_b = DEFAULT_MIN_BUILD
    try:
        rec_b = int(_get_setting(db, "recommended_app_build") or DEFAULT_RECOMMENDED)
    except (TypeError, ValueError):
        rec_b = DEFAULT_RECOMMENDED
    try:
        latest_b = int(_get_setting(db, "latest_app_build") or DEFAULT_LATEST_BUILD)
    except (TypeError, ValueError):
        latest_b = DEFAULT_LATEST_BUILD

    latest_name = _get_setting(db, "latest_app_version") or DEFAULT_LATEST_NAME
    download_url = _get_setting(db, "apk_download_url") or "https://api.belsi.ru/static/belsi-work-latest.apk"
    changelog = _get_setting(db, "apk_changelog") or "Исправления и улучшения"

    # Узнать, на какой версии текущий клиент (по headers)
    client_build_str = request.headers.get("x-app-build")
    try:
        client_build = int(client_build_str) if client_build_str else None
    except (TypeError, ValueError):
        client_build = None

    update_required = False
    update_recommended = False
    if client_build is not None:
        update_required = client_build < min_b
        update_recommended = (not update_required) and client_build < rec_b

    return {
        "latest_version": latest_name,
        "latest_build": latest_b,
        "min_supported_build": min_b,
        "recommended_build": rec_b,
        "client_build": client_build,
        "update_required": update_required,
        "update_recommended": update_recommended,
        "download_url": download_url,
        "changelog": changelog,
    }
