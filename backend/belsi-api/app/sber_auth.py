import base64
import hashlib
import os
import secrets
import urllib.parse
from datetime import datetime, timedelta

import httpx
from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import RedirectResponse, JSONResponse

from .settings import settings
from .db import SessionLocal
from .models import User
from .schemas_sber import SberInitResponse, SberExchangeRequest, SberAuthResponse
from .auth import create_jwt_token

# Если у вас уже есть redis в otp_service.py — используйте тот же клиент/подход.
from .otp_service import redis  # пример: у вас Redis уже используется под OTP

router = APIRouter(prefix="/auth/sber", tags=["auth-sber"])


def _pkce_verifier() -> str:
    # 43-128 символов, URL-safe
    return secrets.token_urlsafe(64)

def _pkce_challenge(verifier: str) -> str:
    digest = hashlib.sha256(verifier.encode("utf-8")).digest()
    return base64.urlsafe_b64encode(digest).decode("utf-8").rstrip("=")

def _redis_key(state: str) -> str:
    return f"sber_oauth:{state}"

def _make_auth_url(state: str, nonce: str, code_challenge: str) -> str:
    params = {
        "response_type": "code",
        "client_id": settings.SBER_CLIENT_ID,
        "redirect_uri": settings.SBER_REDIRECT_URI,
        "scope": getattr(settings, "SBER_SCOPES", "openid profile phone"),
        "state": state,
        "nonce": nonce,
        "code_challenge": code_challenge,
        "code_challenge_method": "S256",
    }
    return settings.SBER_AUTH_URL + "?" + urllib.parse.urlencode(params)


@router.post("/init", response_model=SberInitResponse)
async def sber_init():
    state = secrets.token_urlsafe(32)
    nonce = secrets.token_urlsafe(32)
    verifier = _pkce_verifier()
    challenge = _pkce_challenge(verifier)

    # TTL 10 минут
    await redis.set(_redis_key(state), f"{nonce}:{verifier}", ex=600)

    return SberInitResponse(
        auth_url=_make_auth_url(state, nonce, challenge),
        state=state,
        nonce=nonce,
        code_challenge=challenge,
    )


async def _exchange_code_for_tokens(code: str, code_verifier: str) -> dict:
    data = {
        "grant_type": "authorization_code",
        "client_id": settings.SBER_CLIENT_ID,
        "client_secret": settings.SBER_CLIENT_SECRET,
        "redirect_uri": settings.SBER_REDIRECT_URI,
        "code": code,
        "code_verifier": code_verifier,
    }
    async with httpx.AsyncClient(timeout=20) as client:
        r = await client.post(settings.SBER_TOKEN_URL, data=data)
    if r.status_code != 200:
        raise HTTPException(status_code=401, detail=f"Token exchange failed: {r.text}")
    return r.json()


async def _fetch_userinfo(access_token: str) -> dict:
    headers = {"Authorization": f"Bearer {access_token}"}
    async with httpx.AsyncClient(timeout=20) as client:
        r = await client.get(settings.SBER_USERINFO_URL, headers=headers)
    if r.status_code != 200:
        raise HTTPException(status_code=401, detail=f"Userinfo failed: {r.text}")
    return r.json()


def _get_or_create_user(phone: str) -> User:
    db = SessionLocal()
    try:
        u = db.query(User).filter(User.phone == phone).first()
        if u:
            return u
        u = User(phone=phone, role="installer")  # роль по умолчанию
        db.add(u)
        db.commit()
        db.refresh(u)
        return u
    finally:
        db.close()


@router.get("/callback")
async def sber_callback(request: Request):
    # Сбер вернёт ?code=...&state=...
    code = request.query_params.get("code")
    state = request.query_params.get("state")
    if not code or not state:
        raise HTTPException(status_code=400, detail="Missing code/state")

    saved = await redis.get(_redis_key(state))
    if not saved:
        raise HTTPException(status_code=400, detail="Invalid/expired state")

    saved = saved.decode("utf-8") if isinstance(saved, (bytes, bytearray)) else saved
    nonce, verifier = saved.split(":", 1)

    tokens = await _exchange_code_for_tokens(code, verifier)
    access_token = tokens.get("access_token")
    if not access_token:
        raise HTTPException(status_code=401, detail="No access_token in token response")

    userinfo = await _fetch_userinfo(access_token)

    # ВАЖНО: поле телефона зависит от того, что Сбер реально отдаёт.
    # Обычно это может быть phone_number или аналог (проверите по userinfo).
    phone = userinfo.get("phone_number") or userinfo.get("phone")
    if not phone:
        # Если телефона нет — надо хранить "sub" и маппить в отдельной таблице identities.
        raise HTTPException(status_code=400, detail="No phone in userinfo; need identities mapping by sub")

    # Приводим к формату +7...
    if phone.startswith("7") and not phone.startswith("+"):
        phone = "+" + phone

    _get_or_create_user(phone)

    # JWT-токен
    token = create_jwt_token(phone)

    # Вариант 1: вернуть JSON (если callback открывался в webview и ждёт JSON)
    accept = request.headers.get("accept", "")
    if "application/json" in accept:
        return JSONResponse({"status": "ok", "token": token, "phone": phone})

    # Вариант 2: редирект в приложение (deep link)
    deeplink = getattr(settings, "SBER_APP_DEEPLINK", None)
    if deeplink:
        url = f"{deeplink}?token={urllib.parse.quote(token)}&phone={urllib.parse.quote(phone)}"
        return RedirectResponse(url=url)

    # Вариант 3: показать страницу/сообщение
    return JSONResponse({"status": "ok", "token": token, "phone": phone})
