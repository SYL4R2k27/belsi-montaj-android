#app/main.py

from __future__ import annotations

import re
import secrets
import random
import string
from datetime import datetime, timezone, timedelta
from typing import Optional, List

from fastapi import (
    BackgroundTasks,
    FastAPI,
    Depends,
    HTTPException,
    UploadFile,
    File,
    Form,
    status,
    Query,
)
from fastapi.middleware.cors import CORSMiddleware
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from pydantic import BaseModel, ConfigDict
from uuid import UUID

from sqlalchemy.orm import Session
from sqlalchemy import or_, text

from .db import get_db
from .models import User, Shift, ShiftPhoto, ForemanInvite
from .otp_service import otp_service
from .sms import send_otp_via_smsru, SmsSendError
from .storage import save_shift_photo

# Import routers
from .yandex_auth import router as yandex_auth_router
from .shift_admin import admin_router as shift_admin_router
from .sber_auth import router as sber_auth_router
from .tools import router as tools_router
from .user_names import router as user_router
from .shifts_photos import router as shifts_photos_router
from .admin_devices import router as admin_devices_router
from .version_router import router as version_router
from .user_stats import router as user_stats_router
from .photos_feed import router as photos_feed_router
from .photo_review import router as photo_review_router
from .foreman_team import router as foreman_team_router
from .push_notifications import router as push_router, send_otp_push
from .foreman import router as foreman_router
from .profiles import router as profile_router
from .tasks import router as tasks_router
from .curator import router as curator_router
from .coordinator import router as coordinator_router
# FIX(2026-05-11) BELSI 2.0.0: driver/logistician domain
from .driver_logist import driver_router as driver_router, logist_router as logist_router
# FIX(2026-05-11) BELSI 2.0.0 build3: brand-core (multi-role + timeline + pipeline + idle reasons)
from .brand_core import (
    user_role_router as brand_user_role_router,
    admin_role_router as brand_admin_role_router,
    timeline_router as brand_timeline_router,
    idle_router as brand_idle_router,
    pipeline_router as brand_pipeline_router,
)
# FIX(2026-05-11) BELSI 2.0.0 build4: capability matrix как single source of truth
from .capabilities import caps_router as brand_caps_router
from .reports import router as reports_router
from .shift_pauses import router as shift_pauses_router
from .site_objects import router as site_objects_router
from .support_chat import router as support_chat_router
from .support import router as support_router
from .messenger import router as messenger_router
from .ws_messenger import router as ws_messenger_router
from .auth_login import router as auth_login_router
from .shift_closer import shift_closer_loop
# FIX(2026-05-06): BELSI.Команда — производство (1.3.0, локально)
from .production_batches import router as production_batches_router
from .factory_shifts import router as factory_shifts_router
# FIX(2026-05-06): production-domain полная реализация (variant C)
from .production_brigade import router as production_brigade_router
from .production_materials import router as production_materials_router
from .production_engineer import router as production_engineer_router
# FIX(2026-05-10): AI integration через XeroCode Gateway (1.3.0)
from .voice_input import router as voice_input_router
from .ai_extras import (
    router_messenger as ai_messenger_router,
    router_support as ai_support_router,
    router_materials as ai_materials_router,
)
# FIX(2026-05-11) BELSI 2.0.0: audit endpoint для Update Gate
from .audit_router import router as audit_router
# FIX(2026-05-11) BELSI 2.0.0 (L3): rate-limiter для AI endpoints
from .ai_rate_limit import ai_rate_limit

from .auth import get_current_user, create_jwt_token

app = FastAPI(title="BELSI.Work API")

# CORS — only allow our domains
app.add_middleware(
    CORSMiddleware,
    allow_origins=["https://api.belsi.ru", "https://mobileapp.belsi.ru"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.on_event("startup")
async def startup_event():
    import asyncio
    asyncio.create_task(shift_closer_loop())

    # Safe migrations
    try:
        from .db import get_db
        db = next(get_db())
        db.execute(text("""
            ALTER TABLE coordinator_reports
            ADD COLUMN IF NOT EXISTS curator_feedback TEXT
        """))
        # Site objects: add columns to shifts and shift_photos
        db.execute(text("""
            ALTER TABLE shifts ADD COLUMN IF NOT EXISTS site_object_id UUID
        """))
        db.execute(text("""
            ALTER TABLE shift_photos ADD COLUMN IF NOT EXISTS site_object_id UUID
        """))
        # Create shift_segments table
        db.execute(text("""
            CREATE TABLE IF NOT EXISTS shift_segments (
                id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                shift_id UUID NOT NULL REFERENCES shifts(id) ON DELETE CASCADE,
                site_object_id UUID NOT NULL,
                started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
                ended_at TIMESTAMP WITH TIME ZONE,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
            )
        """))
        # Ensure description/file_urls columns exist on site_objects
        db.execute(text("""
            ALTER TABLE site_objects ADD COLUMN IF NOT EXISTS description TEXT
        """))
        db.execute(text("""
            ALTER TABLE site_objects ADD COLUMN IF NOT EXISTS file_urls JSONB DEFAULT '[]'::jsonb
        """))
        db.execute(text("""
            ALTER TABLE site_objects ADD COLUMN IF NOT EXISTS photo_urls JSONB DEFAULT '[]'::jsonb
        """))
        db.execute(text("""
            ALTER TABLE site_objects ADD COLUMN IF NOT EXISTS created_by UUID
        """))
        # Indexes
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shifts_site_object ON shifts(site_object_id)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shift_photos_site_object ON shift_photos(site_object_id)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shift_segments_shift ON shift_segments(shift_id)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shift_segments_object ON shift_segments(site_object_id)"))

        # Performance indexes (added by optimizer)
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shifts_user_status ON shifts(user_id, status)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shifts_user_start ON shifts(user_id, start_at DESC)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shift_photos_shift_created ON shift_photos(shift_id, created_at)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shift_photos_shift_status ON shift_photos(shift_id, status)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_shift_pauses_shift_ended ON shift_pauses(shift_id, ended_at)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_chat_messages_thread_created ON chat_messages_v2(thread_id, created_at DESC)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_foreman_memberships_foreman_status ON foreman_memberships(foreman_user_id, status)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_foreman_memberships_installer_status ON foreman_memberships(installer_user_id, status)"))
        db.execute(text("CREATE INDEX IF NOT EXISTS idx_tasks_assigned_status ON tasks(assigned_to, status)"))
        db.commit()
        db.close()
    except Exception as e:
        print(f"Migration warning (non-fatal): {e}")

# Register all routers
app.include_router(profile_router)
app.include_router(tools_router)
app.include_router(user_router)
app.include_router(tasks_router)
app.include_router(foreman_team_router)
app.include_router(push_router)
app.include_router(admin_devices_router)
app.include_router(version_router)
app.include_router(user_stats_router)
app.include_router(foreman_router)
app.include_router(yandex_auth_router)
app.include_router(shift_admin_router)
app.include_router(sber_auth_router)
app.include_router(support_chat_router)
app.include_router(support_router)
app.include_router(shifts_photos_router)
app.include_router(photos_feed_router)
app.include_router(photo_review_router)
app.include_router(curator_router)
app.include_router(coordinator_router)
# FIX(2026-05-11) BELSI 2.0.0: driver/logistician routers
app.include_router(driver_router)
app.include_router(logist_router)
# FIX(2026-05-11) BELSI 2.0.0 build3: brand-core routers
app.include_router(brand_user_role_router)
app.include_router(brand_admin_role_router)
app.include_router(brand_timeline_router)
app.include_router(brand_idle_router)
app.include_router(brand_pipeline_router)
# FIX(2026-05-11) BELSI 2.0.0 build4: capability matrix
app.include_router(brand_caps_router)
app.include_router(reports_router)
app.include_router(shift_pauses_router)
app.include_router(site_objects_router)
app.include_router(messenger_router)
app.include_router(ws_messenger_router)
app.include_router(auth_login_router)
# FIX(2026-05-06): BELSI.Команда (1.3.0, локально, без деплоя)
app.include_router(production_batches_router)
app.include_router(factory_shifts_router)
# FIX(2026-05-06): production-domain (variant C)
app.include_router(production_brigade_router)
app.include_router(production_materials_router)
app.include_router(production_engineer_router)
# FIX(2026-05-10): AI integration через XeroCode (1.3.0) — 4 router.
# FIX(2026-05-11) BELSI 2.0.0 (L3): per-user rate limit на AI вызовы (60/мин)
# применяется через dependencies на уровне роутера — защищает квоту XeroCode
# от одиночного юзера, шквалящего AI-запросами.
app.include_router(voice_input_router, dependencies=[Depends(ai_rate_limit)])
app.include_router(ai_messenger_router, dependencies=[Depends(ai_rate_limit)])
app.include_router(ai_support_router, dependencies=[Depends(ai_rate_limit)])
app.include_router(ai_materials_router, dependencies=[Depends(ai_rate_limit)])
# FIX(2026-05-11) BELSI 2.0.0: audit endpoint для Update Gate (без rate limit)
app.include_router(audit_router)

security = HTTPBearer(auto_error=False)


# ==================== УТИЛИТЫ ====================


def normalize_phone(raw: str) -> str:
    digits = re.sub(r"\D", "", raw or "")
    if not digits:
        return ""
    if digits.startswith("8") and len(digits) == 11:
        digits = "7" + digits[1:]
    if not digits.startswith("7") or len(digits) != 11:
        return ""
    return "+" + digits


def get_or_create_user(db: Session, phone: str, default_role: str = "installer") -> tuple:
    """Возвращает (user, is_new)."""
    user = db.query(User).filter(User.phone == phone).one_or_none()
    if user is None:
        user = User(phone=phone, role=default_role)
        db.add(user)
        db.commit()
        db.refresh(user)
        return user, True
    return user, False


# ==================== МОДЕЛИ AUTH ====================


class PhoneRequest(BaseModel):
    phone: str


class OTPVerifyRequest(BaseModel):
    phone: str
    code: str


class VerifyResponse(BaseModel):
    status: str
    token: str
    phone: str
    is_new: bool = False
    role: str = "installer"


# ==================== AUTH ====================


@app.post("/auth/phone")
async def auth_phone(payload: PhoneRequest, db: Session = Depends(get_db)):
    phone = normalize_phone(payload.phone)
    if not phone:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Некорректный номер телефона",
        )
    code = otp_service.generate_code()
    otp_service.save_code(phone, code, ttl_seconds=300)
    
    # Try to send push notification first (faster, free)
    push_sent = send_otp_push(phone, code, db)
    
    # Always send SMS as fallback
    try:
        await send_otp_via_smsru(phone, code)
    except SmsSendError as e:
        # If push was sent, we can still proceed
        if not push_sent:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Ошибка отправки SMS: {e}",
            )
    return {"status": "ok", "message": "verification_code_sent", "push_sent": push_sent}


@app.post("/auth/verify", response_model=VerifyResponse)
def auth_verify(payload: OTPVerifyRequest, db: Session = Depends(get_db)):
    phone = normalize_phone(payload.phone)
    if not phone:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Некорректный номер телефона",
        )
    ok = otp_service.verify_code(phone, payload.code)
    if not ok:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Неверный или истёкший код",
        )
    user, is_new = get_or_create_user(db, phone)
    token = create_jwt_token(phone)
    return VerifyResponse(status="ok", token=token, phone=phone, is_new=is_new, role=user.role or "installer")


# ==================== SHIFTS ====================


class ShiftStartRequest(BaseModel):
    site_object_id: Optional[UUID] = None


class ShiftStartResponse(BaseModel):
    id: UUID
    start_at: datetime
    status: str


class ShiftFinishRequest(BaseModel):
    shift_id: UUID
    total_seconds: int = 0
    pause_seconds: int = 0
    idle_seconds: int = 0
    idle_reason: Optional[str] = None


class ShiftFinishResponse(BaseModel):
    id: UUID
    start_at: datetime
    finish_at: datetime
    duration_hours: float
    status: str


class ShiftItem(BaseModel):
    id: UUID
    start_at: datetime
    finish_at: Optional[datetime]
    duration_hours: Optional[float]
    status: str


class ShiftsListResponse(BaseModel):
    items: List[ShiftItem]


@app.post("/shifts/start", response_model=ShiftStartResponse)
def start_shift(
    payload: ShiftStartRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    active_shift = (
        db.query(Shift)
        .filter(Shift.user_id == current_user.id, Shift.status == "active")
        .one_or_none()
    )
    if active_shift is not None:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Active shift already exists",
        )

    # FIX(2026-05-01): fallback на users.current_site_object_id (sticky binding).
    # Если клиент не прислал site_object_id, но юзер ранее выбрал объект —
    # используем его. Это предохраняет от потери привязки при minimize/kill app.
    if not payload.site_object_id and current_user.current_site_object_id:
        payload.site_object_id = current_user.current_site_object_id
        import logging
        logging.getLogger("shifts").info(
            f"start_shift: using sticky site_object_id={payload.site_object_id} for user {current_user.phone}"
        )

    # FIX(2026-04-30): требование выбора объекта.
    # REQUIRE_SITE_OBJECT=1 в .env — строгий режим (после раскатки APK 1.1.0+).
    # По умолчанию — мягкий: только log warning, чтобы старые APK не сломались.
    import os
    if not payload.site_object_id:
        if os.getenv("REQUIRE_SITE_OBJECT", "0") == "1":
            raise HTTPException(
                status_code=status.HTTP_400_BAD_REQUEST,
                detail="Выберите объект перед началом смены",
            )
        else:
            import logging
            logging.getLogger("shifts").warning(
                f"shift started without site_object_id by user {current_user.phone}"
            )

    now = datetime.now(timezone.utc)
    shift = Shift(user_id=current_user.id, start_at=now, status="active")

    # Привязка к объекту
    if payload.site_object_id:
        from .models import SiteObject, ShiftSegment
        obj = db.query(SiteObject).filter(SiteObject.id == payload.site_object_id).first()
        if obj:
            shift.site_object_id = obj.id

    db.add(shift)
    db.flush()  # get shift.id

    # Создать первый сегмент
    if payload.site_object_id and shift.site_object_id:
        from .models import ShiftSegment
        segment = ShiftSegment(
            shift_id=shift.id,
            site_object_id=shift.site_object_id,
            started_at=now,
        )
        db.add(segment)

    # Sync sticky binding на users.current_site_object_id
    if shift.site_object_id and current_user.current_site_object_id != shift.site_object_id:
        current_user.current_site_object_id = shift.site_object_id

    db.commit()
    db.refresh(shift)
    return ShiftStartResponse(id=shift.id, start_at=shift.start_at, status=shift.status)


@app.post("/shifts/finish", response_model=ShiftFinishResponse)
def finish_shift(
    payload: ShiftFinishRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    # FIX(2026-04-30): SELECT FOR UPDATE SKIP LOCKED — устраняет race с shift_closer.
    shift = (
        db.query(Shift)
        .filter(Shift.id == payload.shift_id, Shift.user_id == current_user.id)
        .with_for_update(skip_locked=True)
        .one_or_none()
    )
    if shift is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Смена не найдена, обрабатывается или принадлежит другому пользователю",
        )
    if shift.status != "active":
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Нельзя завершить смену со статусом {shift.status}",
        )
    now = datetime.now(timezone.utc)
    delta = now - shift.start_at
    # FIX(2026-04-30): защита от часов, ушедших назад (NTP glitch и т.п.)
    if delta.total_seconds() < 0:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Некорректное время: время окончания раньше времени старта",
        )
    duration_hours = round(delta.total_seconds() / 3600.0, 2)
    shift.finish_at = now
    shift.duration_hours = duration_hours
    shift.status = "finished"

    # FIX(2026-04-25): закрыть активную паузу, если монтажник нажал
    # «Закрыть смену» прямо во время паузы — иначе она останется зомби.
    db.execute(
        text("""
            UPDATE shift_pauses
               SET ended_at = :now,
                   duration_seconds = GREATEST(
                       0,
                       EXTRACT(EPOCH FROM (CAST(:now AS timestamptz) - started_at))::int
                   )
             WHERE shift_id = :sid AND ended_at IS NULL
        """),
        {"now": now, "sid": str(shift.id)},
    )

    # FIX(2026-04-25): пересчитать pause_seconds из реальных пауз —
    # источник истины это таблица shift_pauses.
    total_pause_row = db.execute(
        text("""
            SELECT COALESCE(SUM(duration_seconds), 0)::bigint AS s
              FROM shift_pauses
             WHERE shift_id = :sid AND duration_seconds IS NOT NULL
        """),
        {"sid": str(shift.id)},
    ).first()
    shift.pause_seconds = int(total_pause_row.s if total_pause_row else 0)

    # FIX(2026-04-25): записать total_seconds — главный косяк до этого фикса.
    # Рабочее время = wall-clock − паузы − простои.
    wall_seconds = int(delta.total_seconds())
    work_seconds = wall_seconds - int(shift.pause_seconds or 0) - int(shift.idle_seconds or 0)
    shift.total_seconds = max(0, work_seconds)

    # Close active segment (if any)
    from .models import ShiftSegment
    active_segment = (
        db.query(ShiftSegment)
        .filter(ShiftSegment.shift_id == shift.id, ShiftSegment.ended_at == None)
        .first()
    )
    if active_segment:
        active_segment.ended_at = now

    db.add(shift)
    db.commit()
    db.refresh(shift)
    return ShiftFinishResponse(
        id=shift.id,
        start_at=shift.start_at,
        finish_at=shift.finish_at,
        duration_hours=float(shift.duration_hours or 0.0),
        status=shift.status,
    )


@app.get("/shifts", response_model=ShiftsListResponse)
def list_shifts(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    shifts = (
        db.query(Shift)
        .filter(Shift.user_id == current_user.id)
        .order_by(Shift.start_at.desc())
        .all()
    )
    items: List[ShiftItem] = []
    for s in shifts:
        items.append(
            ShiftItem(
                id=s.id,
                start_at=s.start_at,
                finish_at=s.finish_at,
                duration_hours=float(s.duration_hours) if s.duration_hours is not None else None,
                status=s.status,
            )
        )
    return ShiftsListResponse(items=items)


@app.get("/shifts/{shift_id}")
def get_shift_detail(
    shift_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get details of a single shift by ID."""
    shift = (
        db.query(Shift)
        .filter(Shift.id == shift_id, Shift.user_id == current_user.id)
        .one_or_none()
    )
    if shift is None:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="Смена не найдена",
        )

    photos = (
        db.query(ShiftPhoto)
        .filter(ShiftPhoto.shift_id == shift_id)
        .order_by(ShiftPhoto.created_at.asc())
        .all()
    )

    return {
        "id": str(shift.id),
        "user_id": str(shift.user_id),
        "start_at": shift.start_at.isoformat() if shift.start_at else None,
        "finish_at": shift.finish_at.isoformat() if shift.finish_at else None,
        "duration_hours": float(shift.duration_hours) if shift.duration_hours else None,
        "hourly_rate": float(shift.hourly_rate) if shift.hourly_rate else None,
        "status": shift.status,
        "created_at": shift.created_at.isoformat() if shift.created_at else None,
        "photos": [
            {
                "id": str(p.id),
                "hour_label": p.hour_label,
                "photo_url": p.photo_url,
                "status": p.status,
                "comment": p.comment,
                "created_at": p.created_at.isoformat() if p.created_at else None,
            }
            for p in photos
        ],
    }


# ===================== FOREMAN INVITES =====================

class ForemanInviteOut(BaseModel):
    id: UUID
    code: str
    foreman_phone: str
    installer_phone: Optional[str] = None
    status: str
    created_at: datetime
    expires_at: Optional[datetime] = None
    used_at: Optional[datetime] = None

    class Config:
        from_attributes = True


class ForemanInviteListOut(BaseModel):
    items: List[ForemanInviteOut]


class ForemanInviteRedeemIn(BaseModel):
    code: str


class ForemanInviteCancelIn(BaseModel):
    code: str


def _generate_invite_code(length: int = 6) -> str:
    alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "".join(secrets.choice(alphabet) for _ in range(length))


def _get_now_utc() -> datetime:
    return datetime.now(timezone.utc)


def _get_foreman_active_invites_count(db: Session, foreman_phone: str) -> int:
    now = _get_now_utc()
    return (
        db.query(ForemanInvite)
        .filter(
            ForemanInvite.foreman_phone == foreman_phone,
            ForemanInvite.status.in_(["new", "accepted"]),
            or_(ForemanInvite.expires_at.is_(None), ForemanInvite.expires_at > now),
        )
        .count()
    )


def _find_invite_by_code_for_redeem(db: Session, code: str) -> Optional[ForemanInvite]:
    return db.query(ForemanInvite).filter(ForemanInvite.code == code).first()


def _find_invite_by_code_for_foreman(db: Session, code: str, foreman_phone: str) -> Optional[ForemanInvite]:
    return (
        db.query(ForemanInvite)
        .filter(ForemanInvite.code == code, ForemanInvite.foreman_phone == foreman_phone)
        .first()
    )


@app.post("/foreman/invites", response_model=ForemanInviteOut)
def create_foreman_invite(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    foreman_phone = current_user.phone
    active_count = _get_foreman_active_invites_count(db, foreman_phone)
    if active_count >= 10:
        raise HTTPException(status_code=400, detail="Максимум 10 активных инвайтов")
    max_attempts = 10
    code = None
    for _ in range(max_attempts):
        candidate = _generate_invite_code()
        existing = db.query(ForemanInvite).filter(ForemanInvite.code == candidate).first()
        if not existing:
            code = candidate
            break
    if code is None:
        raise HTTPException(status_code=500, detail="Не удалось сгенерировать уникальный код")
    now = _get_now_utc()
    expires_at = now + timedelta(days=3)
    invite = ForemanInvite(
        code=code,
        foreman_phone=foreman_phone,
        foreman_user_id=current_user.id,
        installer_phone=None,
        installer_user_id=None,
        created_at=now,
        expires_at=expires_at,
        used_at=None,
        status="new",
    )
    db.add(invite)
    db.commit()
    db.refresh(invite)
    return ForemanInviteOut.from_orm(invite)


@app.get("/foreman/invites", response_model=ForemanInviteListOut)
def list_foreman_invites(
    status: Optional[str] = Query(None),
    only_active: bool = Query(False),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    foreman_phone = current_user.phone
    now = _get_now_utc()
    query = db.query(ForemanInvite).filter(ForemanInvite.foreman_phone == foreman_phone)
    if status is not None:
        query = query.filter(ForemanInvite.status == status)
    if only_active:
        query = query.filter(
            ForemanInvite.status.in_(["new", "accepted"]),
            or_(ForemanInvite.expires_at.is_(None), ForemanInvite.expires_at > now),
        )
    invites = query.order_by(ForemanInvite.created_at.desc()).all()
    return ForemanInviteListOut(items=[ForemanInviteOut.from_orm(i) for i in invites])


@app.post("/foreman/invites/redeem", response_model=ForemanInviteOut)
def redeem_foreman_invite(
    payload: dict,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    code = payload.get("code")
    if not code:
        raise HTTPException(status_code=400, detail="Не передан инвайт-код")
    invite = _find_invite_by_code_for_redeem(db, code)
    if invite is None:
        raise HTTPException(status_code=404, detail="Инвайт-код не найден")
    now = _get_now_utc()
    if invite.expires_at is not None and invite.expires_at <= now:
        invite.status = "expired"
        db.commit()
        raise HTTPException(status_code=400, detail="Срок действия инвайт-кода истёк")
    if invite.status != "new":
        raise HTTPException(status_code=400, detail="Инвайт-код уже использован или отменён")
    invite.installer_user_id = current_user.id
    invite.installer_phone = current_user.phone
    invite.used_at = datetime.now(timezone.utc)
    invite.status = "accepted"
    db.commit()
    db.refresh(invite)
    return ForemanInviteOut.from_orm(invite)


@app.post("/foreman/invites/cancel", response_model=ForemanInviteOut)
def cancel_foreman_invite(
    payload: dict,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    code = payload.get("code")
    if not code:
        raise HTTPException(status_code=400, detail="Не передан инвайт-код")
    foreman_phone = current_user.phone
    invite = _find_invite_by_code_for_foreman(db, code, foreman_phone)
    if invite is None:
        raise HTTPException(status_code=404, detail="Инвайт-код не найден")
    if invite.status != "new":
        raise HTTPException(status_code=400, detail="Код уже использован, отменён или истёк")
    invite.status = "cancelled"
    db.commit()
    db.refresh(invite)
    return ForemanInviteOut.from_orm(invite)


# ==================== HOURLY PHOTOS ====================


class HourPhotoResponse(BaseModel):
    id: UUID
    created_at: datetime
    hour_label: Optional[str]
    status: str
    comment: Optional[str]
    photo_url: str
    shift_id: Optional[UUID]
    category: str = "hourly"
    ai_comment: Optional[str] = None


@app.post("/shift/hour/photo", response_model=HourPhotoResponse)
async def upload_shift_hour_photo(
    hour_label: str = Form(..., description="Например, 10:00-11:00"),
    photo: UploadFile = File(...),
    shift_id: Optional[UUID] = Form(None),
    comment: Optional[str] = Form(None),
    category: Optional[str] = Form("hourly"),
    background_tasks: BackgroundTasks = BackgroundTasks(),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    content = await photo.read()
    if not content:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Пустой файл")
    try:
        photo_url = await save_shift_photo(content, photo.filename)
    except Exception as e:
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail=f"Ошибка хранения фото: {e}")
    if shift_id is not None:
        shift = db.query(Shift).filter(Shift.id == shift_id, Shift.user_id == current_user.id).one_or_none()
        if shift is None:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Смена не найдена")
    else:
        shift = db.query(Shift).filter(Shift.user_id == current_user.id, Shift.status == "active").order_by(Shift.start_at.desc()).first()
        if shift is None:
            raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Нет активной смены, передайте shift_id")
    now = datetime.now(timezone.utc)
    valid_categories = ["hourly", "problem", "question"]
    photo_category = category if category in valid_categories else "hourly"
    # Auto-assign site_object_id from shift's current object
    photo_site_object_id = getattr(shift, 'site_object_id', None)
    photo_row = ShiftPhoto(shift_id=shift.id, hour_label=hour_label, photo_url=photo_url, status="pending", created_at=now, comment=comment, category=photo_category)
    if photo_site_object_id:
        photo_row.site_object_id = photo_site_object_id
    db.add(photo_row)
    db.commit()
    db.refresh(photo_row)

    # Запускаем AI-анализ фото в фоне (не блокирует ответ)
    from .photo_analysis import run_photo_analysis
    background_tasks.add_task(run_photo_analysis, str(photo_row.id), photo_url)

    return HourPhotoResponse(
        id=photo_row.id, created_at=photo_row.created_at, hour_label=photo_row.hour_label,
        status=photo_row.status, comment=photo_row.comment, photo_url=photo_row.photo_url,
        shift_id=photo_row.shift_id, category=photo_row.category,
    )


# ==================== СИСТЕМНЫЕ РОУТЫ ====================


@app.get("/health")
def health():
    return {"status": "ok"}
