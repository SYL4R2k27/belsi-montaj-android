from __future__ import annotations

from datetime import datetime
from typing import List, Optional, Literal
from uuid import UUID

from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException, Query, UploadFile, File
from pydantic import BaseModel, ConfigDict, Field
from sqlalchemy.orm import Session
from sqlalchemy import asc, desc, func, and_

from .db import get_db
from .auth import get_current_user
from .models import SupportTicket, SupportMessage, User, SupportChatRead
from .push_notifications import send_chat_message_notification

router = APIRouter(prefix="/support/chat", tags=["support-chat"])


# ---------------------
# Pydantic (v2)
# ---------------------
class ChatMessageOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    ticket_id: UUID
    sender_role: str  # user/foreman/curator/system
    sender_user_id: Optional[UUID] = None
    text: str
    photo_url: Optional[str] = None
    voice_url: Optional[str] = None
    voice_duration_seconds: Optional[float] = None
    message_type: str = "text"  # text/photo/voice
    is_internal: bool
    created_at: datetime


class ChatMessageCreate(BaseModel):
    # curator может передать ticket_id, обычный пользователь - нет
    ticket_id: Optional[UUID] = None
    text: str = Field(min_length=1, max_length=4000)
    photo_url: Optional[str] = None
    voice_url: Optional[str] = None
    voice_duration_seconds: Optional[float] = None
    message_type: str = "text"  # text/photo/voice


class ChatInboxItemOut(BaseModel):
    ticket_id: UUID
    user_id: UUID
    user_phone: str
    user_role: str
    last_message_at: Optional[datetime] = None
    last_message_text: Optional[str] = None
    unread_count: int = 0


class ChatMarkReadIn(BaseModel):
    ticket_id: UUID
    last_read_message_id: Optional[UUID] = None


def normalize_sender_role(role: str) -> str:
    r = (role or "").strip().lower()
    if r in ("installer", "user", ""):
        return "user"
    if r == "foreman":
        return "foreman"
    if r == "curator":
        return "curator"
    if r == "system":
        return "system"
    return "user"


def map_sender_role_db(user_role: str | None) -> str:
    r = (user_role or "").strip().lower()
    if r == "curator":
        return "CURATOR"
    if r == "foreman":
        return "FOREMAN"
    if r == "system":
        return "SYSTEM"
    return "USER"


def require_curator(current_user: User):
    if (current_user.role or "").lower() != "curator":
        raise HTTPException(status_code=403, detail="Curator only")


def get_or_create_chat_ticket(db: Session, user_id: UUID) -> SupportTicket:
    ticket = (
        db.query(SupportTicket)
        .filter(SupportTicket.user_id == user_id)
        .filter(SupportTicket.category == "chat")
        .order_by(desc(SupportTicket.created_at))
        .first()
    )
    if ticket:
        return ticket

    ticket = SupportTicket(
        user_id=user_id,
        title="Chat",
        category="chat",
        status="open",
        meta={"title": "Chat"},
    )
    db.add(ticket)
    db.commit()
    db.refresh(ticket)
    return ticket


def build_message_out(m: SupportMessage) -> ChatMessageOut:
    return ChatMessageOut(
        id=m.id,
        ticket_id=m.ticket_id,
        sender_role=normalize_sender_role(getattr(m, "sender_role", "")),
        sender_user_id=getattr(m, "sender_user_id", None),
        text=m.text,
        photo_url=m.photo_url,
        voice_url=getattr(m, "voice_url", None),
        voice_duration_seconds=float(m.voice_duration_seconds) if getattr(m, "voice_duration_seconds", None) else None,
        message_type=getattr(m, "message_type", None) or "text",
        is_internal=bool(m.is_internal),
        created_at=m.created_at,
    )


@router.get("/messages", response_model=List[ChatMessageOut])
def get_chat_messages(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    limit: int = Query(1000, ge=1, le=10000),
    before: Optional[datetime] = Query(None),
    after: Optional[datetime] = Query(None),
    order: Literal["asc", "desc"] = Query("asc"),
    ticket_id: Optional[UUID] = Query(None),
):
    if before and after:
        raise HTTPException(status_code=400, detail="Use only one of 'before' or 'after'")

    # curator can read any ticket_id; normal user reads own chat-ticket
    if ticket_id:
        require_curator(current_user)
        ticket = db.query(SupportTicket).filter(SupportTicket.id == ticket_id).first()
        if not ticket:
            raise HTTPException(status_code=404, detail="Ticket not found")
    else:
        ticket = get_or_create_chat_ticket(db, current_user.id)

    q = (
        db.query(SupportMessage)
        .filter(SupportMessage.ticket_id == ticket.id)
        .filter(SupportMessage.is_internal.is_(False))
    )

    if before:
        q = q.filter(SupportMessage.created_at < before)
    if after:
        q = q.filter(SupportMessage.created_at > after)

    if order == "asc":
        q = q.order_by(asc(SupportMessage.created_at), asc(SupportMessage.id))
    else:
        q = q.order_by(desc(SupportMessage.created_at), desc(SupportMessage.id))

    rows = q.limit(limit).all()
    return [build_message_out(m) for m in rows]


@router.post("/messages", response_model=ChatMessageOut)
def post_chat_message(
    payload: ChatMessageCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    # curator can post into explicit ticket_id
    import sys; print(f"DEBUG payload: text={payload.text!r}, photo_url={payload.photo_url!r}", file=sys.stderr)
    if payload.ticket_id:
        require_curator(current_user)
        ticket = db.query(SupportTicket).filter(SupportTicket.id == payload.ticket_id).first()
        if not ticket:
            raise HTTPException(status_code=404, detail="Ticket not found")
    else:
        ticket = get_or_create_chat_ticket(db, current_user.id)

    msg = SupportMessage(
        ticket_id=ticket.id,
        sender_user_id=current_user.id,
        sender_role=map_sender_role_db(current_user.role),
        text=payload.text,
        is_internal=False,
        photo_url=payload.photo_url,
        voice_url=payload.voice_url,
        voice_duration_seconds=payload.voice_duration_seconds,
        message_type=payload.message_type or "text",
    )
    db.add(msg)
    db.commit()
    db.refresh(msg)

    # --- Push-уведомление получателю ---
    try:
        is_curator = (current_user.role or "").lower() == "curator"
        sender_name = current_user.full_name if hasattr(current_user, 'full_name') else (current_user.phone or "")
        sender_phone = current_user.phone or ""

        if is_curator:
            # Куратор отправил → уведомить пользователя (владельца тикета)
            send_chat_message_notification(
                user_id=ticket.user_id,
                ticket_id=str(ticket.id),
                sender_name=sender_name,
                sender_phone=sender_phone,
                message_text=payload.text,
                is_curator=True,
                db=db,
            )
        else:
            # Пользователь отправил → уведомить всех кураторов
            curators = db.query(User).filter(User.role == "curator").all()
            for curator in curators:
                send_chat_message_notification(
                    user_id=curator.id,
                    ticket_id=str(ticket.id),
                    sender_name=sender_name,
                    sender_phone=sender_phone,
                    message_text=payload.text,
                    is_curator=False,
                    db=db,
                )
    except Exception as e:
        # Не ломаем отправку сообщения если push не прошёл
        import logging
        logging.getLogger(__name__).error(f"Failed to send chat push: {e}")

    return build_message_out(msg)


@router.get("/inbox", response_model=List[ChatInboxItemOut])
def curator_inbox(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
    limit: int = Query(20, ge=1, le=200),
    offset: int = Query(0, ge=0),
    q: Optional[str] = Query(None, description="phone part search"),
    order: Literal["asc", "desc"] = Query("desc"),
):
    require_curator(current_user)

    # tickets: category=chat
    tickets_q = (
        db.query(SupportTicket, User)
        .join(User, User.id == SupportTicket.user_id)
        .filter(SupportTicket.category == "chat")
    )

    if q:
        qq = q.strip()
        if qq:
            tickets_q = tickets_q.filter(User.phone.ilike(f"%{qq}%"))

    # last message subquery per ticket
    last_msg_subq = (
        db.query(
            SupportMessage.ticket_id.label("t_id"),
            func.max(SupportMessage.created_at).label("last_at"),
        )
        .filter(SupportMessage.is_internal.is_(False))
        .group_by(SupportMessage.ticket_id)
        .subquery()
    )

    # join tickets with last message timestamp
    rows = (
        tickets_q
        .outerjoin(last_msg_subq, last_msg_subq.c.t_id == SupportTicket.id)
        .all()
    )

    # build mapping ticket_id -> last message (text/id/created_at)
    # get last message records in one query
    ticket_ids = [t.id for (t, u) in rows]
    last_msgs = {}
    if ticket_ids:
        # fetch last message per ticket via correlated max(created_at)
        lm_rows = (
            db.query(SupportMessage)
            .join(
                last_msg_subq,
                and_(
                    last_msg_subq.c.t_id == SupportMessage.ticket_id,
                    last_msg_subq.c.last_at == SupportMessage.created_at,
                ),
            )
            .filter(SupportMessage.ticket_id.in_(ticket_ids))
            .filter(SupportMessage.is_internal.is_(False))
            .all()
        )
        for m in lm_rows:
            last_msgs[m.ticket_id] = m

    # reads for curator
    reads = {}
    if ticket_ids:
        r_rows = (
            db.query(SupportChatRead)
            .filter(SupportChatRead.ticket_id.in_(ticket_ids))
            .filter(SupportChatRead.reader_user_id == current_user.id)
            .all()
        )
        for r in r_rows:
            reads[r.ticket_id] = r

    out: List[ChatInboxItemOut] = []
    for t, u in rows:
        lm = last_msgs.get(t.id)
        last_at = lm.created_at if lm else None
        last_text = lm.text if lm else None

        # unread_count:
        # count messages after last_read_at (fallback: 0 if no messages)
        unread = 0
        if lm:
            r = reads.get(t.id)
            last_read_at = r.last_read_at if r else None
            if last_read_at is None:
                # never read -> count all
                unread = (
                    db.query(func.count(SupportMessage.id))
                    .filter(SupportMessage.ticket_id == t.id)
                    .filter(SupportMessage.is_internal.is_(False))
                    .scalar()
                ) or 0
            else:
                unread = (
                    db.query(func.count(SupportMessage.id))
                    .filter(SupportMessage.ticket_id == t.id)
                    .filter(SupportMessage.is_internal.is_(False))
                    .filter(SupportMessage.created_at > last_read_at)
                    .scalar()
                ) or 0

        out.append(
            ChatInboxItemOut(
                ticket_id=t.id,
                user_id=u.id,
                user_phone=u.phone,
                user_role=(u.role or "").lower() or "user",
                last_message_at=last_at,
                last_message_text=last_text,
                unread_count=int(unread),
            )
        )

    # sort by last_message_at
    def sort_key(item: ChatInboxItemOut):
        from datetime import timezone
        return item.last_message_at or datetime(1970, 1, 1, tzinfo=timezone.utc)

    reverse = (order == "desc")
    out.sort(key=sort_key, reverse=reverse)

    # pagination after sort
    return out[offset: offset + limit]


@router.post("/read")
def mark_chat_read(
    payload: ChatMarkReadIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Mark ticket as read by current_user (curator or user).
    For curator: read any chat ticket.
    For normal user: only own chat ticket.
    """
    ticket = db.query(SupportTicket).filter(SupportTicket.id == payload.ticket_id).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    is_curator = (current_user.role or "").lower() == "curator"
    if not is_curator and ticket.user_id != current_user.id:
        raise HTTPException(status_code=403, detail="Forbidden")

    # choose last_read_at:
    # if last_read_message_id provided -> take its created_at
    last_read_at = datetime.utcnow()
    if payload.last_read_message_id:
        m = (
            db.query(SupportMessage)
            .filter(SupportMessage.id == payload.last_read_message_id)
            .filter(SupportMessage.ticket_id == ticket.id)
            .first()
        )
        if not m:
            raise HTTPException(status_code=400, detail="Message not found for this ticket")
        last_read_at = m.created_at

    r = (
        db.query(SupportChatRead)
        .filter(SupportChatRead.ticket_id == ticket.id)
        .filter(SupportChatRead.reader_user_id == current_user.id)
        .first()
    )
    if r:
        r.last_read_message_id = payload.last_read_message_id
        r.last_read_at = last_read_at
    else:
        r = SupportChatRead(
            ticket_id=ticket.id,
            reader_user_id=current_user.id,
            last_read_message_id=payload.last_read_message_id,
            last_read_at=last_read_at,
        )
        db.add(r)

    db.commit()
    return {"status": "ok"}


# ============================================
# Voice Upload Endpoint
# ============================================
import uuid as _uuid

VOICE_UPLOAD_DIR = Path("/opt/belsi-api/uploads/voice")
VOICE_MAX_SIZE = 10 * 1024 * 1024  # 10MB
VOICE_ALLOWED_TYPES = [
    "audio/mp4", "audio/m4a", "audio/aac",
    "audio/ogg", "audio/mpeg", "audio/wav",
    "audio/x-m4a", "application/octet-stream",
]
VOICE_BASE_URL = "https://api.belsi.ru"


@router.post("/upload-voice")
async def upload_chat_voice(
    voice: UploadFile = File(...),
    duration: float = Query(0.0, description="Duration in seconds"),
    current_user: User = Depends(get_current_user),
):
    """
    Upload a voice message for chat.

    - Max file size: 10MB
    - Allowed formats: M4A, AAC, OGG, MP3, WAV
    - Returns voice_url and duration_seconds
    """
    from .schemas import ChatVoiceUploadResponse

    # Validate content type (allow application/octet-stream as fallback)
    if voice.content_type and voice.content_type not in VOICE_ALLOWED_TYPES:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid file type: {voice.content_type}. Allowed audio formats: M4A, AAC, OGG, MP3, WAV",
        )

    contents = await voice.read()
    if len(contents) > VOICE_MAX_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"File too large. Max size: {VOICE_MAX_SIZE / 1024 / 1024}MB",
        )

    # Determine extension
    ext = "m4a"
    if voice.filename and "." in voice.filename:
        ext = voice.filename.rsplit(".", 1)[-1].lower()
    if ext not in ("m4a", "aac", "ogg", "mp3", "wav", "mp4"):
        ext = "m4a"

    unique_filename = f"voice_{_uuid.uuid4()}.{ext}"

    VOICE_UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    file_path = VOICE_UPLOAD_DIR / unique_filename

    try:
        with open(file_path, "wb") as f:
            f.write(contents)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to save voice: {str(e)}")

    voice_url = f"{VOICE_BASE_URL}/uploads/voice/{unique_filename}"

    return ChatVoiceUploadResponse(
        voice_url=voice_url,
        duration_seconds=max(duration, 0.0),
    )
