"""
Модуль авторизации по логину (телефон / email / username) + пароль.
Добавляет POST /auth/login и утилиты для управления паролями.
"""
from __future__ import annotations

import re
from datetime import datetime, timezone

import bcrypt
from fastapi import APIRouter, Depends, HTTPException, status
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import or_

from .db import get_db
from .models import User
from .auth import create_jwt_token, get_current_user

router = APIRouter(tags=["auth"])


# ─── Schemas ──────────────────────────────────────────────

class LoginRequest(BaseModel):
    login: str       # телефон, email или username
    password: str


class LoginResponse(BaseModel):
    token: str
    user: dict


# ─── Password utilities ──────────────────────────────────

def hash_password(password: str) -> str:
    """Хэширует пароль через bcrypt"""
    return bcrypt.hashpw(password.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def verify_password(password: str, password_hash: str) -> bool:
    """Проверяет пароль против bcrypt хэша"""
    try:
        return bcrypt.checkpw(password.encode("utf-8"), password_hash.encode("utf-8"))
    except Exception:
        return False


def normalize_phone(raw: str) -> str:
    """Нормализация номера телефона"""
    digits = re.sub(r"\D", "", raw or "")
    if not digits:
        return ""
    if digits.startswith("8") and len(digits) == 11:
        digits = "7" + digits[1:]
    if not digits.startswith("7") or len(digits) != 11:
        return ""
    return "+" + digits


def find_user_by_login(db: Session, login: str) -> User | None:
    """
    Ищет пользователя по:
    1. Номеру телефона (нормализованному)
    2. Email (case-insensitive)
    3. Username (case-insensitive)
    """
    # Попробуем как телефон
    phone = normalize_phone(login)
    if phone:
        user = db.query(User).filter(User.phone == phone).first()
        if user:
            return user

    # Попробуем как email
    user = db.query(User).filter(
        User.email.isnot(None),
        User.email.ilike(login)
    ).first()
    if user:
        return user

    # Попробуем как username
    user = db.query(User).filter(
        User.username.isnot(None),
        User.username.ilike(login)
    ).first()
    if user:
        return user

    # Попробуем по сырому номеру (на случай если пользователь ввёл без +7)
    if login.startswith("7") or login.startswith("8") or login.startswith("+7"):
        phone_alt = normalize_phone(login)
        if phone_alt:
            user = db.query(User).filter(User.phone == phone_alt).first()
            if user:
                return user

    return None


def user_to_dict(user: User) -> dict:
    """Конвертирует User в dict для ответа"""
    return {
        "id": str(user.id),
        "phone": user.phone,
        "role": user.role or "installer",
        "first_name": user.first_name,
        "last_name": user.last_name,
        "full_name": user.full_name,
        "short_id": user.short_id,
        "foreman_id": str(user.foreman_id) if user.foreman_id else None,
        "email": user.email,
        "username": user.username,
        "created_at": user.created_at.isoformat() if user.created_at else None,
    }


# ─── Endpoint ─────────────────────────────────────────────

@router.post("/auth/login", response_model=LoginResponse)
def login_by_credentials(
    payload: LoginRequest,
    db: Session = Depends(get_db),
):
    """
    Авторизация по логину + паролю.
    Логин может быть: номер телефона, email или username.
    Возвращает JWT токен и данные пользователя.
    """
    if not payload.login or not payload.login.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Логин не может быть пустым",
        )
    if not payload.password:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Пароль не может быть пустым",
        )

    user = find_user_by_login(db, payload.login.strip())

    if user is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Неверный логин или пароль",
        )

    # Проверяем пароль
    if not user.password_hash:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Для этого аккаунта не установлен пароль. Используйте вход по SMS.",
        )

    if not verify_password(payload.password, user.password_hash):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Неверный логин или пароль",
        )

    # Создаём JWT токен
    token = create_jwt_token(user.phone)

    return LoginResponse(
        token=token,
        user=user_to_dict(user),
    )


# ─── Schemas (password management) ────────────────────────

class ChangePasswordRequest(BaseModel):
    current_password: str
    new_password: str


class SetPasswordRequest(BaseModel):
    user_id: str
    new_password: str


class PasswordResponse(BaseModel):
    success: bool
    message: str


# ─── Password endpoints ───────────────────────────────────

@router.post("/auth/change-password", response_model=PasswordResponse)
def change_own_password(
    payload: ChangePasswordRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Смена своего пароля.
    Требует текущий пароль + новый пароль.
    """
    if len(payload.new_password) < 6:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Пароль должен быть не менее 6 символов",
        )

    # Проверяем текущий пароль
    if current_user.password_hash:
        if not verify_password(payload.current_password, current_user.password_hash):
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Неверный текущий пароль",
            )
    else:
        # Если у пользователя ещё нет пароля — пропускаем проверку
        pass

    current_user.password_hash = hash_password(payload.new_password)
    db.commit()

    return PasswordResponse(success=True, message="Пароль успешно изменён")


@router.post("/curator/set-password", response_model=PasswordResponse)
def curator_set_password(
    payload: SetPasswordRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Куратор устанавливает пароль любому пользователю.
    Не требует знания текущего пароля.
    """
    if current_user.role != "curator":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="Только куратор может устанавливать пароли",
        )

    if len(payload.new_password) < 6:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Пароль должен быть не менее 6 символов",
        )

    target_user = db.query(User).filter(User.id == payload.user_id).first()
    if not target_user:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Пользователь не найден",
        )

    target_user.password_hash = hash_password(payload.new_password)
    db.commit()

    return PasswordResponse(success=True, message="Пароль установлен")


# ════════════════════════════════════════════════════════════════
# SELF-SIGNUP — V1 (без email/SMS подтверждения)
# FIX(2026-05-04): юзер сам регистрирует учётку с телефон+пароль+имя+роль.
# Email сохраняется но не верифицируется (V1 принимает любой текст).
# Колонки email_verified/signup_status готовы для V2 (одобрение куратором).
# ════════════════════════════════════════════════════════════════

import uuid as _uuid_mod

class SignUpRequest(BaseModel):
    phone: str
    password: str
    first_name: str
    last_name: str
    role: str
    email: str | None = None
    invite_code: str | None = None  # на будущее (V2)


class SignUpResponse(BaseModel):
    token: str
    user: dict
    message: str = "Учётка создана"


def _validate_password(pw: str) -> str | None:
    """Возвращает None если пароль валиден, иначе текст ошибки."""
    if len(pw) < 8:
        return "Пароль должен быть не короче 8 символов"
    if not any(c.isdigit() for c in pw):
        return "Пароль должен содержать хотя бы одну цифру"
    if not any(c.isalpha() for c in pw):
        return "Пароль должен содержать хотя бы одну букву"
    return None


def _validate_role(role: str) -> str:
    """Проверяет что роль допустимая. Возвращает нормализованную."""
    role = role.strip().lower()
    allowed = {"installer", "foreman", "coordinator", "curator"}
    if role not in allowed:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Недопустимая роль. Допустимые: {sorted(allowed)}",
        )
    return role


def _notify_curators_about_signup(db: Session, new_user: User) -> None:
    """Отправить push всем кураторам о новой регистрации."""
    try:
        from .push_notifications import send_fcm_notification
        from sqlalchemy import text as sa_text
        rows = db.execute(
            sa_text("SELECT id, fcm_token FROM users WHERE role IN ('curator', 'coordinator') AND fcm_token IS NOT NULL")
        ).all()
        full_name = f"{new_user.first_name or ''} {new_user.last_name or ''}".strip() or new_user.phone
        title = "Новый пользователь зарегистрирован"
        body = f"{full_name} ({new_user.role}) — {new_user.phone}"
        for r in rows:
            send_fcm_notification(
                token=r.fcm_token,
                title=title,
                body=body,
                data={
                    "type": "signup_new",
                    "user_id": str(new_user.id),
                    "user_name": full_name,
                    "user_role": new_user.role,
                    "user_phone": new_user.phone,
                },
            )
    except Exception as e:
        import logging
        logging.getLogger("signup").warning(f"notify curators failed: {e}")


def _send_welcome_push(new_user: User) -> None:
    """Welcome push новому пользователю."""
    if not new_user.fcm_token:
        return
    try:
        from .push_notifications import send_fcm_notification
        full_name = (new_user.first_name or "").strip()
        send_fcm_notification(
            token=new_user.fcm_token,
            title=("Добро пожаловать" + ((", " + full_name) if full_name else "") + "!"),
            body="Учётка BELSI создана. Можно начинать работу.",
            data={"type": "signup_welcome"},
        )
    except Exception as e:
        import logging
        logging.getLogger("signup").warning(f"welcome push failed: {e}")


@router.post("/auth/signup", response_model=SignUpResponse)
def signup(
    payload: SignUpRequest,
    db: Session = Depends(get_db),
):
    """
    Самостоятельная регистрация (V1 — без email/SMS подтверждения).
    Учётка сразу активна.

    Возвращает JWT-токен и данные юзера — клиент сразу логинит.
    """
    # 1) Нормализуем + валидируем телефон
    phone = normalize_phone(payload.phone)
    if not re.match(r"^\+?\d{10,15}$", phone):
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Неверный формат телефона",
        )

    # 2) Проверяем пароль
    pw_err = _validate_password(payload.password)
    if pw_err:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=pw_err)

    # 3) Имя обязательное
    first_name = (payload.first_name or "").strip()
    last_name = (payload.last_name or "").strip()
    if not first_name:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Имя обязательно",
        )

    # 4) Роль
    role = _validate_role(payload.role)

    # 5) Email опционально
    email = (payload.email or "").strip().lower() or None
    if email and "@" not in email:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Неверный формат email",
        )

    # 6) Уникальность телефона
    if db.query(User).filter(User.phone == phone).first():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Учётка с таким телефоном уже существует. Войдите по паролю.",
        )

    # 7) Уникальность email (если задан)
    if email and db.query(User).filter(User.email == email).first():
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="Email уже используется другой учёткой",
        )

    # 8) Создаём
    new_user = User(
        id=_uuid_mod.uuid4(),
        phone=phone,
        first_name=first_name,
        last_name=last_name or None,
        role=role,
        email=email,
        password_hash=hash_password(payload.password),
        # V2-поля (на будущее):
        # email_verified=False,  # default уже TRUE для V1
        # signup_status="active",  # default
    )
    db.add(new_user)
    db.commit()
    db.refresh(new_user)

    # 9) Пуши (best-effort, не падаем если что)
    _notify_curators_about_signup(db, new_user)
    _send_welcome_push(new_user)

    # 10) JWT-токен — юзер сразу залогинен
    token = create_jwt_token(new_user.phone)

    return SignUpResponse(
        token=token,
        user=user_to_dict(new_user),
        message="Учётка создана",
    )
