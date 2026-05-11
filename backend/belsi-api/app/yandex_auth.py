import logging
import secrets
import urllib.parse
from datetime import date as date_cls
from typing import Optional, Tuple

import httpx
import sqlalchemy.exc

from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import RedirectResponse
from pydantic import BaseModel

from .settings import get_settings
from .db import SessionLocal
from .models import User
from .auth import create_jwt_token

router = APIRouter(prefix="/auth/yandex", tags=["auth-yandex"])
log = logging.getLogger("yandex_auth")


class YandexTokenRequest(BaseModel):
    yandex_token: str


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _avatar_url_from_id(avatar_id: Optional[str], size: str = "islands-200") -> Optional[str]:
    """Yandex отдаёт default_avatar_id, склеиваем URL по их шаблону."""
    if not avatar_id:
        return None
    return f"https://avatars.yandex.net/get-yapic/{avatar_id}/{size}"


def _parse_birthday(raw: Optional[str]) -> Optional[date_cls]:
    """Yandex отдаёт 'YYYY-MM-DD' или None. Если что-то иное — игнорируем без падения."""
    if not raw:
        return None
    try:
        return date_cls.fromisoformat(raw)
    except (ValueError, TypeError):
        return None


def _enrich_user_from_yandex(user: User, info: dict) -> bool:
    """Заполняем пустые поля профиля данными от Яндекса. Возвращает True если что-то изменилось."""
    changed = False

    first_name = (info.get("first_name") or "").strip() or None
    last_name = (info.get("last_name") or "").strip() or None
    email = (info.get("default_email") or "").strip() or None
    avatar_id = info.get("default_avatar_id")
    avatar_url = _avatar_url_from_id(avatar_id)
    birthday = _parse_birthday(info.get("birthday"))

    # Заполняем только пустые поля, чтобы не затирать ручные правки пользователя.
    if first_name and not user.first_name:
        user.first_name = first_name
        changed = True
    if last_name and not user.last_name:
        user.last_name = last_name
        changed = True
    if email and not user.email:
        user.email = email
        changed = True
    if avatar_url and not user.avatar_url:
        user.avatar_url = avatar_url
        changed = True
    if birthday and not user.birthday:
        user.birthday = birthday
        changed = True

    return changed


def _maybe_upgrade_phone(db, user: User, real_phone: Optional[str]) -> None:
    """
    Если у юзера phone был плейсхолдером 'yandex:<id>' и Яндекс отдал реальный телефон —
    апгрейдим. Если новый номер уже занят другим юзером — оставляем как есть (не падаем).
    """
    if not real_phone:
        return
    current = user.phone or ""
    if not current.startswith("yandex:"):
        return  # Уже реальный — не трогаем

    user.phone = real_phone
    try:
        db.flush()
    except sqlalchemy.exc.IntegrityError:
        db.rollback()
        log.warning(
            "phone upgrade conflict: user=%s wanted phone=%s, already used elsewhere",
            user.id,
            real_phone,
        )
        # Возвращаем плейсхолдер, чтобы транзакция не сломалась
        user.phone = current


def _find_or_create_yandex_user(
    db, yandex_id: str, real_phone: Optional[str], info: dict
) -> Tuple[User, bool]:
    """
    Логика поиска (приоритет — стабильный oauth_subject):
      1. Ищем по oauth_subject = yandex_id (новая логика)
      2. Fallback: ищем по phone = 'yandex:<id>' (старая логика для уже мигрированных)
      3. Если не нашли — создаём нового юзера

    Возвращает (user, is_new).
    """
    # 1. По oauth_subject
    user = (
        db.query(User)
        .filter(User.oauth_provider == "yandex", User.oauth_subject == yandex_id)
        .first()
    )

    # 2. Fallback по phone
    if not user:
        user = db.query(User).filter(User.phone == f"yandex:{yandex_id}").first()
        if user:
            # Backfill oauth-полей — синхронно с миграцией
            user.oauth_provider = "yandex"
            user.oauth_subject = yandex_id

    # 3. Создаём нового
    is_new = user is None
    if is_new:
        # Реальный телефон если есть, иначе плейсхолдер 'yandex:<id>'
        phone = real_phone if real_phone else f"yandex:{yandex_id}"
        user = User(
            phone=phone,
            role="installer",
            oauth_provider="yandex",
            oauth_subject=yandex_id,
        )
        db.add(user)
        db.flush()  # Получаем user.id для дальнейших операций

    return user, is_new


# ---------------------------------------------------------------------------
# /start (web flow)
# ---------------------------------------------------------------------------

@router.get("/start")
def yandex_start():
    """
    Открываем это из клиента (ASWebAuthenticationSession / CustomTabs).
    Сервер делает redirect на Яндекс OAuth authorize.
    """
    s = get_settings()
    state = secrets.token_urlsafe(24)

    params = {
        "response_type": "code",
        "client_id": s.yandex_client_id,
        "redirect_uri": s.yandex_redirect_uri,
        "scope": s.yandex_scopes,
        "state": state,
    }

    url = f"{s.yandex_auth_url}?{urllib.parse.urlencode(params)}"
    return RedirectResponse(url=url, status_code=302)


# ---------------------------------------------------------------------------
# /callback (web — code → token → userinfo)
# ---------------------------------------------------------------------------

@router.get("/callback")
def yandex_callback(code: str = Query(...), state: str = Query(None)):
    """
    Web-flow: Яндекс вернёт сюда ?code=...&state=...
    Меняем code → access_token, тянем userinfo, обогащаем профиль, редирект в приложение.
    """
    s = get_settings()

    # 1) Exchange code → token
    token_data = {
        "grant_type": "authorization_code",
        "code": code,
        "client_id": s.yandex_client_id,
        "client_secret": s.yandex_client_secret,
        "redirect_uri": s.yandex_redirect_uri,
    }

    try:
        with httpx.Client(timeout=20) as client:
            r = client.post(s.yandex_token_url, data=token_data)
            r.raise_for_status()
            token_json = r.json()
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Yandex token exchange failed: {e}")

    access_token = token_json.get("access_token")
    if not access_token:
        raise HTTPException(status_code=400, detail="No access_token from Yandex")

    # 2) UserInfo
    info = _fetch_userinfo(access_token, s.yandex_userinfo_url)

    yandex_id = str(info.get("id") or info.get("client_id") or "")
    if not yandex_id:
        raise HTTPException(status_code=400, detail="Yandex user id not found")

    real_phone = _extract_real_phone(info)

    # 3) Найти/создать + обогатить
    db = SessionLocal()
    try:
        user, is_new = _find_or_create_yandex_user(db, yandex_id, real_phone, info)
        _enrich_user_from_yandex(user, info)
        _maybe_upgrade_phone(db, user, real_phone)
        db.commit()
        phone_for_token = user.phone
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()

    app_token = create_jwt_token(phone_for_token)
    deeplink = (
        f"{s.yandex_app_deeplink}"
        f"?token={urllib.parse.quote(app_token)}"
        f"&phone={urllib.parse.quote(phone_for_token)}"
    )
    return RedirectResponse(url=deeplink, status_code=302)


# ---------------------------------------------------------------------------
# /callback (mobile — клиент уже получил access_token через SDK)
# ---------------------------------------------------------------------------

@router.post("/callback")
def yandex_callback_mobile(request: YandexTokenRequest):
    """
    Mobile SDK flow: Android/iOS приложение получает OAuth-токен через Yandex AuthSDK
    и отправляет его сюда. Мы тянем userinfo, обогащаем профиль и возвращаем JWT.
    """
    s = get_settings()
    access_token = request.yandex_token

    if not access_token:
        raise HTTPException(status_code=422, detail="yandex_token is required")

    # 1) UserInfo
    info = _fetch_userinfo(access_token, s.yandex_userinfo_url)

    # 2) Парсинг полей
    yandex_id = str(info.get("id") or "")
    if not yandex_id:
        raise HTTPException(status_code=400, detail="Yandex user id not found")

    real_phone = _extract_real_phone(info)

    # 3) Найти/создать пользователя + обогатить профиль
    db = SessionLocal()
    try:
        user, is_new = _find_or_create_yandex_user(db, yandex_id, real_phone, info)
        _enrich_user_from_yandex(user, info)
        _maybe_upgrade_phone(db, user, real_phone)
        db.commit()
        db.refresh(user)

        result = {
            "token": create_jwt_token(user.phone),
            "phone": user.phone,
            "is_new": is_new,
            "role": user.role or "installer",
        }
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()

    return result


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _fetch_userinfo(access_token: str, userinfo_url: str) -> dict:
    """GET https://login.yandex.ru/info?format=json с OAuth-токеном."""
    try:
        with httpx.Client(timeout=20) as client:
            r = client.get(
                userinfo_url,
                headers={"Authorization": f"OAuth {access_token}"},
            )
            r.raise_for_status()
            return r.json()
    except httpx.HTTPStatusError as e:
        raise HTTPException(
            status_code=400,
            detail=f"Yandex userinfo failed: {e.response.status_code}",
        )
    except Exception as e:
        raise HTTPException(status_code=400, detail=f"Yandex userinfo failed: {e}")


def _extract_real_phone(info: dict) -> Optional[str]:
    """default_phone приходит как dict {'id': ..., 'number': '+7...'} либо отсутствует."""
    default_phone = info.get("default_phone")
    if isinstance(default_phone, dict):
        number = default_phone.get("number")
        if isinstance(number, str) and number.strip():
            return number.strip()
    return None
