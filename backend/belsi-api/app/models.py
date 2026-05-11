# app/models.py

from __future__ import annotations

import uuid
import enum
from uuid import uuid4
from datetime import datetime
from decimal import Decimal
from typing import Optional

from sqlalchemy import (
    Boolean,
    Column,
    DateTime,
    ForeignKey,
    Numeric,
    String,
    Text,
    UniqueConstraint,
    Enum as SAEnum,
    text as sa_text,
    BigInteger,
    Integer,
    Date,
)
from sqlalchemy.dialects.postgresql import UUID, JSONB
from sqlalchemy.orm import relationship
from sqlalchemy.sql import func

from .db import Base

# =========================
# USERS
# =========================

class User(Base):
    __tablename__ = "users"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    phone = Column(String, unique=True, nullable=False)
    role = Column(String, nullable=False)  # installer / foreman / curator ...
    first_name = Column(String(100), nullable=True)
    last_name = Column(String(100), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    short_id = Column(String(6), unique=True, nullable=True)  # 6-char human-readable ID
    foreman_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    hourly_rate = Column(Numeric(10, 2), nullable=True, server_default=sa_text("0"))
    last_seen = Column(DateTime(timezone=True), nullable=True)

    # Auth fields
    password_hash = Column(Text, nullable=True)
    email = Column(String, nullable=True)
    username = Column(String, nullable=True)
    fcm_token = Column(String, nullable=True)

    # Sticky binding пользователь<->объект, независимо от смены
    # FIX(2026-05-01): объект сохраняется при выборе, переживает minimize/kill приложения
    current_site_object_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=True)

    # FIX(2026-05-04) 1.3.0: профильные поля для Yandex OAuth обогащения.
    # Колонки добавляются миграцией yandex_profile_enrich.py (NOT NULL=False, безопасно).
    birthday = Column(Date, nullable=True)
    avatar_url = Column(Text, nullable=True)
    # Стабильный идентификатор от OAuth-провайдера. Безопаснее phone, потому что
    # phone у Yandex-юзера может быть 'yandex:<id>' (плейсхолдер), а oauth_subject — всегда настоящий.
    oauth_provider = Column(Text, nullable=True)  # 'yandex' | 'sber' | None
    oauth_subject = Column(Text, nullable=True)

    @property
    def full_name(self) -> str:
        """Получить полное имя пользователя"""
        if self.first_name and self.last_name:
            return f"{self.first_name} {self.last_name}"
        elif self.first_name:
            return self.first_name
        elif self.last_name:
            return self.last_name
        return self.phone or "Без имени"

    # shifts (ok)
    shifts = relationship("Shift", back_populates="user", foreign_keys="Shift.user_id")

    # profile (optional)
    profile = relationship("UserProfile", uselist=False, back_populates="user", cascade="all, delete-orphan")

    # tasks (optional relationships, без back_populates, чтобы не ловить mapper-конфликты)
    created_tasks = relationship("Task", foreign_keys="Task.created_by")
    assigned_tasks = relationship("Task", foreign_keys="Task.assigned_to")

# =========================
# USER PROFILES (public.user_profiles)
# =========================

class UserProfile(Base):
    __tablename__ = "user_profiles"

    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)

    full_name = Column(Text, nullable=True)
    city = Column(Text, nullable=True)
    email = Column(Text, nullable=True)
    telegram = Column(Text, nullable=True)
    about = Column(Text, nullable=True)

    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)

    user = relationship("User", back_populates="profile")

# =========================
# SHIFTS + PHOTOS
# =========================

class Shift(Base):
    __tablename__ = "shifts"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)

    start_at = Column(DateTime(timezone=True), nullable=False)
    finish_at = Column(DateTime(timezone=True), nullable=True)

    duration_hours = Column(Numeric(8, 4), nullable=True)  # Decimal (в БД может быть другая точность — ок)
    hourly_rate = Column(Numeric(10, 2), nullable=True)  # rub/hour
    status = Column(Text, nullable=False)  # active / finished / approved / paid

    # Timer fields
    total_seconds = Column(BigInteger, nullable=True, server_default=sa_text("0"))
    pause_seconds = Column(BigInteger, nullable=True, server_default=sa_text("0"))
    idle_seconds = Column(BigInteger, nullable=True, server_default=sa_text("0"))
    idle_reason = Column(Text, nullable=True)
    
    # Assignment fields
    foreman_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    curator_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    site_object_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    user = relationship("User", back_populates="shifts", foreign_keys=[user_id])
    photos = relationship("ShiftPhoto", back_populates="shift", cascade="all, delete-orphan")

class ShiftPhoto(Base):
    __tablename__ = "shift_photos"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    shift_id = Column(UUID(as_uuid=True), ForeignKey("shifts.id", ondelete="CASCADE"), nullable=False)
    site_object_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=True)

    hour_label = Column(Text, nullable=True)  # '10:00–11:00'
    photo_url = Column(Text, nullable=False)

    status = Column(Text, nullable=False, server_default=sa_text("'pending'"))  # pending / approved / rejected
    comment = Column(Text, nullable=True)
    category = Column(Text, nullable=False, server_default=sa_text("'hourly'"))  # hourly / problem / question

    # AI-анализ качества фото (заполняется фоновой задачей после загрузки)
    ai_comment = Column(Text, nullable=True)
    ai_score = Column(Integer, nullable=True)
    ai_category = Column(Text, nullable=True)
    ai_analyzed_at = Column(DateTime(timezone=True), nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    shift = relationship("Shift", back_populates="photos")

# =========================
# FOREMAN INVITES
# =========================

class ForemanInvite(Base):
    __tablename__ = "foreman_invites"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    code = Column(String, nullable=False, unique=True)

    foreman_phone = Column(String, nullable=False)
    installer_phone = Column(String, nullable=True)

    foreman_user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    installer_user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    expires_at = Column(DateTime(timezone=True), nullable=True)

    status = Column(String, nullable=False, server_default=sa_text("'new'"))  # new/accepted/cancelled/expired
    used_at = Column(DateTime(timezone=True), nullable=True)

    foreman_user = relationship("User", foreign_keys=[foreman_user_id])
    installer_user = relationship("User", foreign_keys=[installer_user_id])

# =========================
# FOREMAN ↔ INSTALLERS LINK (public.foreman_memberships)
# (реальная таблица у тебя именно foreman_memberships)
# =========================

class ForemanMembership(Base):
    __tablename__ = "foreman_memberships"
    __table_args__ = (
        UniqueConstraint("foreman_user_id", "installer_user_id", name="foreman_memberships_foreman_user_id_installer_user_id_key"),
    )

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=func.gen_random_uuid())
    foreman_user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    installer_user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    status = Column(Text, nullable=False, server_default=sa_text("'active'"))

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    foreman = relationship("User", foreign_keys=[foreman_user_id])

# =========================
# TASKS (public.tasks) — строго по \d+ public.tasks
# =========================

class Task(Base):
    __tablename__ = "tasks"

    id = Column(UUID(as_uuid=True), primary_key=True)  # в БД default gen_random_uuid()

    created_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    assigned_to = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)

    title = Column(Text, nullable=False)
    description = Column(Text, nullable=True)

    status = Column(Text, nullable=False, server_default=sa_text("'new'"))
    priority = Column(Text, nullable=False, server_default=sa_text("'normal'"))

    due_at = Column(DateTime(timezone=True), nullable=True)

    meta = Column(JSONB, nullable=False, server_default=sa_text("'{}'::jsonb"))

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)

    creator = relationship("User", foreign_keys=[created_by])

# =========================
# SUPPORT
# =========================

class SupportStatus(str, enum.Enum):
    # если в БД это enum support_status с lower-case — оставляем так
    OPEN = "open"
    IN_PROGRESS = "in_progress"
    RESOLVED = "resolved"
    CLOSED = "closed"

class SupportSenderRole(str, enum.Enum):
    # IMPORTANT: у тебя в БД реально хранится FOREMAN/USER (uppercase),
    # иначе ты бы не видел sender_role = 'FOREMAN' в support_messages.
    USER = "USER"
    CURATOR = "CURATOR"
    FOREMAN = "FOREMAN"
    SYSTEM = "SYSTEM"

class SupportTicket(Base):
    __tablename__ = "support_tickets"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)

    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)

    foreman_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)

    curator_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    site_object_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=True)

    title = Column(String, nullable=False)
    category = Column(String, nullable=False)

    # если в БД status TEXT — можно заменить на Column(String)
    status = Column(
        String(32),
        nullable=False,
        default="open",
        server_default=sa_text("'open'"),
    )

    meta = Column(JSONB, nullable=True)

    user = relationship("User", foreign_keys=[user_id])
    foreman = relationship("User", foreign_keys=[foreman_id])
    curator = relationship("User", foreign_keys=[curator_id])

    messages = relationship(
        "SupportMessage",
        back_populates="ticket",
        cascade="all, delete-orphan",
    )

class SupportMessage(Base):
    __tablename__ = "support_messages"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)

    ticket_id = Column(
        UUID(as_uuid=True),
        ForeignKey("support_tickets.id", ondelete="CASCADE"),
        nullable=False,
        index=True,
    )

    sender_role = Column(SAEnum(SupportSenderRole, name="support_sender_role"), nullable=False)
    sender_user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)

    text = Column(Text, nullable=False)
    photo_url = Column(Text, nullable=True)  # Photo attachment URL
    voice_url = Column(Text, nullable=True)  # Voice message URL
    voice_duration_seconds = Column(Numeric, nullable=True)  # Voice message duration
    message_type = Column(String(16), nullable=False, server_default=sa_text("'text'"))  # text/photo/voice
    is_internal = Column(Boolean, nullable=False, server_default=sa_text("false"))

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    ticket = relationship("SupportTicket", back_populates="messages")
    sender = relationship("User", foreign_keys=[sender_user_id])

# READ STATUS (public.support_ticket_reads)
# композитный PK как в SQL, без отдельного id
# =========================

class SupportTicketRead(Base):
    __tablename__ = "support_ticket_reads"

    ticket_id = Column(UUID(as_uuid=True), ForeignKey("support_tickets.id", ondelete="CASCADE"), primary_key=True)
    reader_user_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), primary_key=True)

    last_read_message_id = Column(UUID(as_uuid=True), ForeignKey("support_messages.id", ondelete="SET NULL"), nullable=True)
    last_read_at = Column(DateTime(timezone=True), nullable=True)

    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)

    ticket = relationship("SupportTicket", lazy="joined")
    reader = relationship("User", lazy="joined")

# =========================
# MESSENGER (chat_threads, chat_participants, chat_messages_v2)
# Completely separate from support_messages!
# =========================

class ChatThread(Base):
    __tablename__ = "chat_threads"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    type = Column(String(16), nullable=False)  # direct / group
    name = Column(String(255), nullable=True)  # for groups
    avatar_url = Column(Text, nullable=True)
    created_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    creator = relationship("User", foreign_keys=[created_by])
    participants = relationship("ChatParticipant", back_populates="thread", cascade="all, delete-orphan")
    messages = relationship("ChatMessageV2", back_populates="thread", cascade="all, delete-orphan")


class ChatParticipant(Base):
    __tablename__ = "chat_participants"
    __table_args__ = (
        UniqueConstraint("thread_id", "user_id", name="uq_chat_participant_thread_user"),
    )

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    thread_id = Column(UUID(as_uuid=True), ForeignKey("chat_threads.id", ondelete="CASCADE"), nullable=False, index=True)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True)
    role = Column(String(16), nullable=False, server_default=sa_text("'member'"))  # admin / member
    muted_until = Column(DateTime(timezone=True), nullable=True)
    last_read_message_id = Column(UUID(as_uuid=True), nullable=True)
    joined_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    thread = relationship("ChatThread", back_populates="participants")
    user = relationship("User", foreign_keys=[user_id])


class ChatMessageV2(Base):
    __tablename__ = "chat_messages_v2"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    thread_id = Column(UUID(as_uuid=True), ForeignKey("chat_threads.id", ondelete="CASCADE"), nullable=False, index=True)
    sender_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    message_type = Column(String(16), nullable=False, server_default=sa_text("'text'"))  # text / photo / voice / system
    text = Column(Text, nullable=True)
    photo_url = Column(Text, nullable=True)
    voice_url = Column(Text, nullable=True)
    voice_duration_seconds = Column(Numeric, nullable=True)
    reply_to_id = Column(UUID(as_uuid=True), nullable=True)
    forwarded_from_id = Column(UUID(as_uuid=True), nullable=True)
    file_url = Column(Text, nullable=True)
    file_name = Column(String(512), nullable=True)
    file_size = Column(Integer, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    thread = relationship("ChatThread", back_populates="messages")
    sender = relationship("User", foreign_keys=[sender_id])


# ============= Tools Module Models =============
# Added: 2026-01-19

from sqlalchemy import Enum as SQLEnum
import enum

class ToolStatus(str, enum.Enum):
    """Статусы инструмента"""
    available = "available"
    issued = "issued"
    lost = "lost"
    repair = "repair"

class TransactionStatus(str, enum.Enum):
    """Статусы транзакции"""
    issued = "issued"
    returned = "returned"

class Tool(Base):
    """Модель инструмента"""
    __tablename__ = "tools"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(String(200), nullable=False, index=True)
    description = Column(Text, nullable=True)
    serial_number = Column(String(100), nullable=True, index=True)
    photo_url = Column(String(500), nullable=True)
    foreman_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True)
    status = Column(SQLEnum(ToolStatus), nullable=False, default=ToolStatus.available, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False)

    foreman = relationship("User", foreign_keys=[foreman_id])
    transactions = relationship("ToolTransaction", back_populates="tool")

class ToolTransaction(Base):
    """Модель транзакции инструмента"""
    __tablename__ = "tool_transactions"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    tool_id = Column(UUID(as_uuid=True), ForeignKey("tools.id"), nullable=False, index=True)
    installer_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False, index=True)
    issued_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    issued_at = Column(DateTime, default=datetime.utcnow, nullable=False, index=True)
    issue_comment = Column(Text, nullable=True)
    issue_photo_url = Column(String(500), nullable=True)
    returned_at = Column(DateTime, nullable=True)
    returned_to = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    return_condition = Column(String(50), nullable=True)
    return_comment = Column(Text, nullable=True)
    return_photo_url = Column(String(500), nullable=True)
    status = Column(SQLEnum(TransactionStatus), nullable=False, default=TransactionStatus.issued, index=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    tool = relationship("Tool", back_populates="transactions")
    installer = relationship("User", foreign_keys=[installer_id])
    issued_by_user = relationship("User", foreign_keys=[issued_by])
    returned_to_user = relationship("User", foreign_keys=[returned_to])

# =========================
# CHAT READ STATUS (public.support_chat_reads)
# =========================

class SupportChatRead(Base):
    """Статус прочитанных сообщений в чате поддержки"""
    __tablename__ = "support_chat_reads"

    ticket_id = Column(
        UUID(as_uuid=True),
        ForeignKey("support_tickets.id", ondelete="CASCADE"),
        primary_key=True
    )
    reader_user_id = Column(
        UUID(as_uuid=True),
        ForeignKey("users.id", ondelete="CASCADE"),
        primary_key=True
    )

    last_read_message_id = Column(
        UUID(as_uuid=True),
        ForeignKey("support_messages.id", ondelete="SET NULL"),
        nullable=True
    )
    last_read_at = Column(DateTime(timezone=True), nullable=True)

    updated_at = Column(
        DateTime(timezone=True),
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False
    )

    ticket = relationship("SupportTicket")
    reader = relationship("User")


# =========================
# SITE OBJECTS (Строительные объекты)
# =========================

class SiteObject(Base):
    """Строительный объект (глобальный, виден всем)"""
    __tablename__ = "site_objects"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    name = Column(Text, nullable=False)
    address = Column(Text, nullable=True)
    description = Column(Text, nullable=True)
    status = Column(Text, nullable=False, server_default=sa_text("'active'"))  # active / completed / archived
    measurements = Column(JSONB, nullable=False, server_default=sa_text("'{}'::jsonb"))
    comments = Column(Text, nullable=True)
    photo_urls = Column(JSONB, nullable=False, server_default=sa_text("'[]'::jsonb"))
    file_urls = Column(JSONB, nullable=False, server_default=sa_text("'[]'::jsonb"))
    latitude = Column(Numeric(10, 7), nullable=True)    # 3.16: подготовка к карте
    longitude = Column(Numeric(10, 7), nullable=True)   # 3.16: подготовка к карте
    created_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    coordinator_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)

    creator = relationship("User", foreign_keys=[created_by])
    coordinator = relationship("User", foreign_keys=[coordinator_id])


# =========================
# SHIFT SEGMENTS (Сегменты смены по объектам)
# =========================

class ShiftSegment(Base):
    """Сегмент смены — период работы на конкретном объекте"""
    __tablename__ = "shift_segments"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid4)
    shift_id = Column(UUID(as_uuid=True), ForeignKey("shifts.id", ondelete="CASCADE"), nullable=False, index=True)
    site_object_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=False, index=True)
    started_at = Column(DateTime(timezone=True), nullable=False, server_default=func.now())
    ended_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    shift = relationship("Shift", foreign_keys=[shift_id])
    site_object = relationship("SiteObject", foreign_keys=[site_object_id])


# =========================
# PRODUCTION BATCHES (Партии — Pipeline производство → логистика → монтаж)
# FIX(2026-05-05): сущность Партия для BELSI.Команда
# =========================

class ProductionBatch(Base):
    """
    Партия — единица товара, проходящая через всю экосистему.
    
    Жизненный цикл:
        draft → in_production → ready_to_ship → in_route → delivered → installed
    """
    __tablename__ = "production_batches"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))
    title = Column(Text, nullable=False)
    description = Column(Text, nullable=True)
    item_count = Column(Integer, nullable=False, server_default=sa_text("0"))

    # Откуда (фабрика производства) и куда (объект-цель)
    source_facility_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=False)
    target_object_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=True)

    status = Column(Text, nullable=False, server_default=sa_text("'draft'"))
    priority = Column(Text, nullable=False, server_default=sa_text("'normal'"))
    deadline = Column(DateTime(timezone=True), nullable=True)

    created_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    responsible_user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), onupdate=func.now(), nullable=False)
    meta = Column(JSONB, nullable=True)


class BatchStatusHistory(Base):
    """Audit log смен статусов партии."""
    __tablename__ = "batch_status_history"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))
    batch_id = Column(UUID(as_uuid=True), ForeignKey("production_batches.id", ondelete="CASCADE"), nullable=False)
    from_status = Column(Text, nullable=True)
    to_status = Column(Text, nullable=False)
    changed_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    changed_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    comment = Column(Text, nullable=True)


# =========================
# IDLE REASONS CATALOG (Справочник причин простоя по доменам)
# =========================

class IdleReasonCatalog(Base):
    __tablename__ = "shift_idle_reason_catalog"

    id = Column(BigInteger, primary_key=True, autoincrement=True)
    domain = Column(Text, nullable=False)  # installation / logistics / production
    code = Column(Text, nullable=False)
    label = Column(Text, nullable=False)
    position = Column(BigInteger, nullable=False, server_default=sa_text("0"))
    active = Column(Boolean, nullable=False, server_default=sa_text("true"))



# =========================
# PRODUCTION DOMAIN (Бригады, Материалы, Инженерные задачи)
# FIX(2026-05-06): полная реализация production-домена
# =========================

class Brigade(Base):
    """Бригада производства — один Старший работник + N Работников."""
    __tablename__ = "brigades"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))
    name = Column(Text, nullable=False)
    facility_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=False)
    senior_worker_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), nullable=True)

    senior = relationship("User", foreign_keys=[senior_worker_id])


class BrigadeMember(Base):
    """Состав бригады. PK составной (brigade_id, user_id)."""
    __tablename__ = "brigade_members"

    brigade_id = Column(UUID(as_uuid=True), ForeignKey("brigades.id", ondelete="CASCADE"), primary_key=True)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), primary_key=True)
    role_in_brigade = Column(Text, nullable=False, server_default=sa_text("'worker'"))
    joined_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class MaterialCatalog(Base):
    """Каталог материалов — общий для всех фабрик."""
    __tablename__ = "materials_catalog"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))
    code = Column(Text, unique=True, nullable=False)
    name = Column(Text, nullable=False)
    unit = Column(Text, nullable=False, server_default=sa_text("'шт'"))
    category = Column(Text, nullable=True)
    min_stock = Column(Integer, nullable=False, server_default=sa_text("0"))
    active = Column(Boolean, nullable=False, server_default=sa_text("true"))
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class MaterialInventory(Base):
    """Остатки материала на конкретной фабрике."""
    __tablename__ = "materials_inventory"

    facility_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), primary_key=True)
    material_id = Column(UUID(as_uuid=True), ForeignKey("materials_catalog.id"), primary_key=True)
    quantity = Column(Integer, nullable=False, server_default=sa_text("0"))
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class MaterialOrder(Base):
    """Заявка на материал от снабженца."""
    __tablename__ = "material_orders"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))
    facility_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=False)
    material_id = Column(UUID(as_uuid=True), ForeignKey("materials_catalog.id"), nullable=False)
    quantity_requested = Column(Integer, nullable=False)
    quantity_delivered = Column(Integer, nullable=True, server_default=sa_text("0"))
    status = Column(Text, nullable=False, server_default=sa_text("'pending'"))
    # pending / approved / ordered / delivered / cancelled
    requested_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    approved_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    supplier_id = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    note = Column(Text, nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


class EngineerTask(Base):
    """Инженерная задача — спец. задание для роли Engineer."""
    __tablename__ = "engineer_tasks"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))
    batch_id = Column(UUID(as_uuid=True), ForeignKey("production_batches.id"), nullable=True)
    facility_id = Column(UUID(as_uuid=True), ForeignKey("site_objects.id"), nullable=True)
    type = Column(Text, nullable=False, server_default=sa_text("'general'"))
    # general / design / tooling / quality_check / tech_doc / repair
    title = Column(Text, nullable=False)
    description = Column(Text, nullable=True)
    assigned_to = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=True)
    status = Column(Text, nullable=False, server_default=sa_text("'open'"))
    # open / in_progress / done / cancelled
    priority = Column(Text, nullable=False, server_default=sa_text("'normal'"))
    # low / normal / high / urgent
    due_at = Column(DateTime(timezone=True), nullable=True)
    completed_at = Column(DateTime(timezone=True), nullable=True)
    created_by = Column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    updated_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)


# =========================
# AI ANALYSES — универсальное хранилище ответов от XeroCode AI Gateway
# FIX(2026-05-10): scope BELSI 1.3.0 AI integration
# =========================

class AiAnalysis(Base):
    """
    Все AI-ответы от XeroCode-gateway хранятся здесь.

    Привязка к одной из сущностей через nullable FK
    (shift_photo_id / shift_id / pause_id / batch_id / user_id).

    analysis_type: photo_quality / daily_summary / idle_verify /
                   voice_transcribe / triage_ticket / chat_reply_suggest /
                   query_to_filter / stock_forecast

    Идемпотентность через UNIQUE request_id.
    Корректировка человеком — через corrected_by/corrected_json.
    """
    __tablename__ = "ai_analyses"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))

    # Привязка (один из, остальные NULL)
    shift_photo_id = Column(UUID(as_uuid=True), ForeignKey("shift_photos.id", ondelete="CASCADE"), nullable=True)
    shift_id = Column(UUID(as_uuid=True), ForeignKey("shifts.id", ondelete="CASCADE"), nullable=True)
    pause_id = Column(UUID(as_uuid=True), ForeignKey("shift_pauses.id", ondelete="CASCADE"), nullable=True)
    batch_id = Column(UUID(as_uuid=True), ForeignKey("production_batches.id", ondelete="CASCADE"), nullable=True)
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)

    # Тип анализа (см. BELSI_PROMPT_TEMPLATES в XeroCode)
    analysis_type = Column(Text, nullable=False)

    # Ответ
    result_json = Column(JSONB, nullable=False)
    result_text = Column(Text, nullable=True)
    confidence = Column(Integer, nullable=True)  # 0-100

    # Метаданные XeroCode
    model_used = Column(Text, nullable=True)
    provider_used = Column(Text, nullable=True)
    tokens_input = Column(Integer, nullable=True)
    tokens_output = Column(Integer, nullable=True)
    cost_usd = Column(Numeric(10, 6), nullable=True)
    duration_ms = Column(Integer, nullable=True)

    # Идемпотентность
    request_id = Column(Text, nullable=False, unique=True)

    # Использовался ли paid fallback (anthropic/openai/apiyi)?
    paid_fallback_used = Column(Boolean, nullable=False, server_default=sa_text("false"))

    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)

    # Корректировка от человека
    corrected_by = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="SET NULL"), nullable=True)
    corrected_at = Column(DateTime(timezone=True), nullable=True)
    corrected_json = Column(JSONB, nullable=True)
    correction_note = Column(Text, nullable=True)


class UpdateConsent(Base):
    """
    FIX(2026-05-11) BELSI 2.0.0: аудит согласий перед major-обновлением.

    Создаётся когда пользователь в Update Gate Compose-диалоге отмечает
    все 6 чекбоксов и тапает «Скачать». Хранится бессрочно — это
    юридический след для 152-ФЗ.

    items — JSONB массив объектов {id, text} (копия 6 чекбоксов на момент
    показа диалога; если редакция текста изменится — старые записи
    остаются с историческим текстом).
    """
    __tablename__ = "update_consents"

    id = Column(UUID(as_uuid=True), primary_key=True, server_default=sa_text("gen_random_uuid()"))
    user_id = Column(UUID(as_uuid=True), ForeignKey("users.id", ondelete="CASCADE"), nullable=False)
    from_version = Column(String(32), nullable=False)
    to_version = Column(String(32), nullable=False)
    agreed_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
    items = Column(JSONB, nullable=False)
    device_info = Column(String(256), nullable=True)
    app_build = Column(Integer, nullable=True)
    ip_address = Column(String(64), nullable=True)
    created_at = Column(DateTime(timezone=True), server_default=func.now(), nullable=False)
