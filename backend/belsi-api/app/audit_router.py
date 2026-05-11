"""
FIX(2026-05-11) BELSI 2.0.0: audit-endpoint для Update Gate.

Принимает POST /audit/update-consent от Compose-диалога обновления.
Записывает в таблицу update_consents юридический след согласий
пользователя перед major-апдейтом (1.2.5 → 2.0.0).

Только при успешной записи фронт продолжает скачивание APK — если
этот endpoint вернёт ошибку, кнопка «Скачать» НЕ срабатывает.
Это требование 152-ФЗ: согласие должно быть зафиксировано
ПЕРЕД обработкой персональных данных в новой версии.
"""
from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Optional, List
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, Field
from sqlalchemy.orm import Session

from .auth import get_current_user
from .db import get_db
from .models import User, UpdateConsent


logger = logging.getLogger("audit")

router = APIRouter(prefix="/audit", tags=["audit"])


class ConsentItem(BaseModel):
    id: str = Field(..., description="Технический id чекбокса: features|ai_helper|xerocode|deprecate|ready|age_18")
    text: str = Field(..., description="Текст чекбокса как он был показан юзеру")


class UpdateConsentRequest(BaseModel):
    from_version: str = Field(..., max_length=32)
    to_version: str = Field(..., max_length=32)
    items: List[ConsentItem]
    device_info: Optional[str] = Field(None, max_length=256, description="Модель устройства, OS version")
    app_build: Optional[int] = None


class UpdateConsentResponse(BaseModel):
    id: UUID
    accepted: bool = True
    agreed_at: datetime
    can_download: bool = True


@router.post("/update-consent", response_model=UpdateConsentResponse)
def submit_update_consent(
    payload: UpdateConsentRequest,
    request: Request,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Записывает согласие пользователя перед major-обновлением.

    Контракт с фронтом:
    - Все 6 чекбоксов отмечены → фронт шлёт payload → сервер записывает
    - Любое из 6 не отмечено → фронт НЕ шлёт (кнопка disabled)
    - Если сервер вернул HTTP 200 + can_download=true → фронт открывает
      Intent на скачивание APK
    - Любая ошибка → юзер видит «Не удалось зарегистрировать согласие,
      попробуйте ещё раз». APK не скачивается.

    Идемпотентность: запись создаётся каждый раз когда юзер тапает
    «Скачать» — это сознательно (юзер может скачать дважды, и оба раза
    мы хотим иметь след). Уникальности по (user_id, to_version) нет.
    """
    # Защита от пустого items
    if not payload.items:
        raise HTTPException(status_code=400, detail="items не может быть пустым")

    # FIX(2026-05-11) BELSI 2.0.0: на 2026-05-11 в спеке 9 чекбоксов
    # (6 про обновление + 3 общих TOS/Privacy/EULA из бывшего экрана регистрации).
    # Если фронт прислал меньше — это либо баг, либо обход. Логируем и отказываем.
    EXPECTED_ITEMS = 9
    if len(payload.items) < EXPECTED_ITEMS:
        logger.warning(
            "update_consent: user=%s sent only %d items (expected >=%d)",
            current_user.id, len(payload.items), EXPECTED_ITEMS,
        )
        raise HTTPException(
            status_code=400,
            detail=f"Ожидалось {EXPECTED_ITEMS} пунктов согласия, получено {len(payload.items)}",
        )

    # Получаем IP клиента (может быть за прокси — берём X-Forwarded-For)
    ip = (
        request.headers.get("x-forwarded-for", "").split(",")[0].strip()
        or (request.client.host if request.client else None)
    )

    try:
        consent = UpdateConsent(
            user_id=current_user.id,
            from_version=payload.from_version,
            to_version=payload.to_version,
            items=[item.model_dump() for item in payload.items],
            device_info=payload.device_info,
            app_build=payload.app_build,
            ip_address=ip[:64] if ip else None,
        )
        db.add(consent)
        db.commit()
        db.refresh(consent)
    except Exception as e:
        db.rollback()
        logger.exception("update_consent: failed to save for user=%s: %s", current_user.id, e)
        raise HTTPException(status_code=500, detail="Не удалось зарегистрировать согласие")

    logger.info(
        "update_consent: user=%s %s → %s items=%d ip=%s",
        current_user.id, payload.from_version, payload.to_version,
        len(payload.items), ip,
    )

    return UpdateConsentResponse(
        id=consent.id,
        accepted=True,
        agreed_at=consent.agreed_at or datetime.now(timezone.utc),
        can_download=True,
    )


@router.get("/update-consent/last", response_model=Optional[UpdateConsentResponse])
def get_last_consent(
    to_version: Optional[str] = None,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Опционально для фронта: проверить, давал ли юзер уже согласие на
    конкретную to_version. Если давал — можно НЕ показывать gate-экран
    при повторном запуске.

    На MVP — фронт может не использовать. Spec говорит «чекбоксы не
    помнятся между сессиями», но если нужно ослабить это — можно
    прочитать сюда.
    """
    q = db.query(UpdateConsent).filter(UpdateConsent.user_id == current_user.id)
    if to_version:
        q = q.filter(UpdateConsent.to_version == to_version)
    consent = q.order_by(UpdateConsent.agreed_at.desc()).first()
    if not consent:
        return None
    return UpdateConsentResponse(
        id=consent.id,
        accepted=True,
        agreed_at=consent.agreed_at,
        can_download=True,
    )
