# app/messenger.py
"""
Messenger module — personal and group chats.
Completely separate from support_chat (support tickets remain untouched).

Tables: chat_threads, chat_participants, chat_messages_v2
"""

from __future__ import annotations

import os
import uuid
from collections import defaultdict
from datetime import datetime, timedelta, timezone, timezone
from typing import Optional, List, Dict, Tuple

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, Query, UploadFile, File, status
from pydantic import BaseModel
from sqlalchemy import func, and_, or_, desc, text as sa_text, literal_column
from sqlalchemy.orm import Session, aliased

from .db import get_db
from .models import (
    User,
    ForemanMembership,
    ChatThread,
    ChatParticipant,
    ChatMessageV2,
)
from .auth import get_current_user
from .push_notifications import send_chat_message_notification
from .ws_messenger import notify_new_message, notify_thread_updated
from .storage import upload_file as s3_upload_file

router = APIRouter(prefix="/messenger", tags=["messenger"])

# =====================================================
# Constants for file upload
# =====================================================

MAX_FILE_SIZE = 50 * 1024 * 1024  # 50 MB
ALLOWED_FILE_EXTENSIONS = {
    ".pdf", ".doc", ".docx", ".xls", ".xlsx",
    ".png", ".jpg", ".jpeg",
    ".zip", ".rar",
    ".dwg", ".dxf",
}

CONTENT_TYPE_MAP = {
    ".pdf": "application/pdf",
    ".doc": "application/msword",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xls": "application/vnd.ms-excel",
    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".zip": "application/zip",
    ".rar": "application/x-rar-compressed",
    ".dwg": "application/acad",
    ".dxf": "application/dxf",
}


# =====================================================
# DTOs
# =====================================================

class ThreadOut(BaseModel):
    id: str
    type: str  # direct / group
    name: Optional[str] = None
    avatar_url: Optional[str] = None
    created_at: str
    updated_at: str
    # populated at runtime
    last_message: Optional["MessageOut"] = None
    unread_count: int = 0
    participants: List["ParticipantOut"] = []


class ParticipantOut(BaseModel):
    user_id: str
    role: str  # admin / member
    full_name: str
    phone: str
    user_role: str  # installer / foreman / curator
    last_seen: Optional[str] = None
    is_online: bool = False


class MessageOut(BaseModel):
    id: str
    thread_id: str
    sender_id: str
    sender_name: str
    sender_role: str  # installer / foreman / curator
    message_type: str  # text / photo / voice / file / system / deleted
    text: Optional[str] = None
    photo_url: Optional[str] = None
    voice_url: Optional[str] = None
    voice_duration_seconds: Optional[float] = None
    file_url: Optional[str] = None
    file_name: Optional[str] = None
    file_size: Optional[int] = None
    forwarded_from: Optional[str] = None  # sender_name of original message
    forwarded_from_id: Optional[str] = None
    reply_to_id: Optional[str] = None
    created_at: str
    is_read: bool = False


class CreateThreadRequest(BaseModel):
    type: str  # direct / group
    name: Optional[str] = None
    participant_ids: List[str]  # user IDs to add


class SendMessageRequest(BaseModel):
    text: Optional[str] = None
    photo_url: Optional[str] = None
    voice_url: Optional[str] = None
    voice_duration_seconds: Optional[float] = None
    message_type: str = "text"  # text / photo / voice / file
    reply_to_id: Optional[str] = None
    file_url: Optional[str] = None
    file_name: Optional[str] = None
    file_size: Optional[int] = None
    forwarded_from_id: Optional[str] = None


class AddMembersRequest(BaseModel):
    user_ids: List[str]


class UpdateThreadRequest(BaseModel):
    name: Optional[str] = None


class ContactOut(BaseModel):
    id: str
    full_name: str
    phone: str
    role: str  # installer / foreman / curator


class ThreadListResponse(BaseModel):
    threads: List[ThreadOut]


class MessagesResponse(BaseModel):
    messages: List[MessageOut]
    has_more: bool = False


class FileUploadResponse(BaseModel):
    file_url: str
    file_name: str
    file_size: int


class SearchMessageOut(BaseModel):
    id: str
    thread_id: str
    thread_name: Optional[str] = None
    sender_id: str
    sender_name: str
    message_type: str
    text: Optional[str] = None
    created_at: str


class SearchResponse(BaseModel):
    messages: List[SearchMessageOut]


# =====================================================
# Helpers
# =====================================================

def _resolve_forwarded_from(msg: ChatMessageV2, db: Session) -> Optional[str]:
    """Resolve forwarded_from sender name from forwarded_from_id."""
    if not msg.forwarded_from_id:
        return None
    original = db.query(ChatMessageV2).filter(ChatMessageV2.id == msg.forwarded_from_id).first()
    if not original:
        return None
    sender = db.query(User).filter(User.id == original.sender_id).first()
    return sender.full_name if sender else "Неизвестный"


def _build_message_out(msg: ChatMessageV2, db: Session) -> MessageOut:
    """Build MessageOut for a single message (used in send_message, etc.).
    Falls back to individual query — use _build_message_out_from_row for batch."""
    sender = db.query(User).filter(User.id == msg.sender_id).first()
    forwarded_from = _resolve_forwarded_from(msg, db)
    return MessageOut(
        id=str(msg.id),
        thread_id=str(msg.thread_id),
        sender_id=str(msg.sender_id),
        sender_name=sender.full_name if sender else "Неизвестный",
        sender_role=sender.role if sender else "unknown",
        message_type=msg.message_type or "text",
        text=msg.text,
        photo_url=msg.photo_url,
        voice_url=msg.voice_url,
        voice_duration_seconds=float(msg.voice_duration_seconds) if msg.voice_duration_seconds else None,
        file_url=msg.file_url,
        file_name=msg.file_name,
        file_size=int(msg.file_size) if msg.file_size else None,
        forwarded_from=forwarded_from,
        forwarded_from_id=str(msg.forwarded_from_id) if msg.forwarded_from_id else None,
        reply_to_id=str(msg.reply_to_id) if msg.reply_to_id else None,
        created_at=msg.created_at.isoformat() if msg.created_at else "",
        is_read=False,
    )


def _build_message_out_with_user(
    msg: ChatMessageV2,
    sender: Optional[User],
    is_read: bool = False,
    forwarded_from: Optional[str] = None,
) -> MessageOut:
    """Build MessageOut when we already have the sender User object."""
    return MessageOut(
        id=str(msg.id),
        thread_id=str(msg.thread_id),
        sender_id=str(msg.sender_id),
        sender_name=sender.full_name if sender else "Неизвестный",
        sender_role=sender.role if sender else "unknown",
        message_type=msg.message_type or "text",
        text=msg.text,
        photo_url=msg.photo_url,
        voice_url=msg.voice_url,
        voice_duration_seconds=float(msg.voice_duration_seconds) if msg.voice_duration_seconds else None,
        file_url=msg.file_url,
        file_name=msg.file_name,
        file_size=int(msg.file_size) if msg.file_size else None,
        forwarded_from=forwarded_from,
        forwarded_from_id=str(msg.forwarded_from_id) if msg.forwarded_from_id else None,
        reply_to_id=str(msg.reply_to_id) if msg.reply_to_id else None,
        created_at=msg.created_at.isoformat() if msg.created_at else "",
        is_read=is_read,
    )


def _build_thread_out(
    thread: ChatThread,
    current_user: User,
    db: Session,
) -> ThreadOut:
    """Build ThreadOut for a single thread (used by create_thread, update_thread, etc.).
    For list_threads, use the batch-optimized path instead."""
    # Participants — single query with JOIN
    participants_db = (
        db.query(ChatParticipant, User)
        .join(User, User.id == ChatParticipant.user_id)
        .filter(ChatParticipant.thread_id == thread.id)
        .all()
    )

    participants = []
    for cp, u in participants_db:
        ls = u.last_seen.isoformat() if getattr(u, 'last_seen', None) else None
        is_on = bool(u.last_seen and u.last_seen > datetime.now(timezone.utc) - timedelta(minutes=5))
        participants.append(ParticipantOut(
            user_id=str(u.id),
            role=cp.role,
            full_name=u.full_name,
            phone=u.phone,
            user_role=u.role,
            last_seen=ls,
            is_online=is_on,
        ))

    # Last message — single query with JOIN to User
    last_msg_row = (
        db.query(ChatMessageV2, User)
        .outerjoin(User, User.id == ChatMessageV2.sender_id)
        .filter(ChatMessageV2.thread_id == thread.id)
        .order_by(ChatMessageV2.created_at.desc())
        .first()
    )

    last_message = None
    if last_msg_row:
        last_msg, sender = last_msg_row
        forwarded_from = _resolve_forwarded_from(last_msg, db)
        last_message = _build_message_out_with_user(last_msg, sender, forwarded_from=forwarded_from)

    # Unread count
    my_participation = (
        db.query(ChatParticipant)
        .filter(
            ChatParticipant.thread_id == thread.id,
            ChatParticipant.user_id == current_user.id,
        )
        .first()
    )

    unread_count = 0
    if my_participation:
        q = db.query(func.count(ChatMessageV2.id)).filter(
            ChatMessageV2.thread_id == thread.id,
            ChatMessageV2.sender_id != current_user.id,
        )
        if my_participation.last_read_message_id:
            last_read_msg = db.query(ChatMessageV2).filter(
                ChatMessageV2.id == my_participation.last_read_message_id
            ).first()
            if last_read_msg:
                q = q.filter(ChatMessageV2.created_at > last_read_msg.created_at)
        unread_count = q.scalar() or 0

    # Thread name for direct chats
    thread_name = thread.name
    if thread.type == "direct" and not thread_name:
        other_participants = [p for p in participants if p.user_id != str(current_user.id)]
        if other_participants:
            thread_name = other_participants[0].full_name

    return ThreadOut(
        id=str(thread.id),
        type=thread.type,
        name=thread_name,
        avatar_url=thread.avatar_url,
        created_at=thread.created_at.isoformat() if thread.created_at else "",
        updated_at=thread.updated_at.isoformat() if thread.updated_at else "",
        last_message=last_message,
        unread_count=unread_count,
        participants=participants,
    )


def _check_is_participant(thread_id: uuid.UUID, user_id: uuid.UUID, db: Session) -> ChatParticipant:
    cp = (
        db.query(ChatParticipant)
        .filter(
            ChatParticipant.thread_id == thread_id,
            ChatParticipant.user_id == user_id,
        )
        .first()
    )
    if not cp:
        raise HTTPException(status_code=403, detail="Вы не участник этого чата")
    return cp


# =====================================================
# Batch helpers for list_threads optimization
# =====================================================

def _batch_build_threads(
    threads: List[ChatThread],
    current_user: User,
    db: Session,
) -> List[ThreadOut]:
    """Build ThreadOut for multiple threads using batch queries (3 queries total
    instead of 3*N).  Returns list in the same order as input threads."""

    if not threads:
        return []

    thread_ids = [t.id for t in threads]

    # --- 1) All participants for all threads (single query) ---
    all_participants_rows = (
        db.query(ChatParticipant, User)
        .join(User, User.id == ChatParticipant.user_id)
        .filter(ChatParticipant.thread_id.in_(thread_ids))
        .all()
    )

    # Group by thread_id
    participants_by_thread: Dict[uuid.UUID, List[ParticipantOut]] = defaultdict(list)
    my_participation_by_thread: Dict[uuid.UUID, ChatParticipant] = {}

    for cp, u in all_participants_rows:
        ls = u.last_seen.isoformat() if getattr(u, 'last_seen', None) else None
        is_on = bool(u.last_seen and u.last_seen > datetime.now(timezone.utc) - timedelta(minutes=5))
        participants_by_thread[cp.thread_id].append(ParticipantOut(
            user_id=str(u.id),
            role=cp.role,
            full_name=u.full_name,
            phone=u.phone,
            user_role=u.role,
            last_seen=ls,
            is_online=is_on,
        ))
        if cp.user_id == current_user.id:
            my_participation_by_thread[cp.thread_id] = cp

    # --- 2) Last message per thread (single query using DISTINCT ON) ---
    # Use a subquery to get the latest message id per thread, then fetch full rows
    latest_msg_subq = (
        db.query(
            ChatMessageV2.thread_id,
            func.max(ChatMessageV2.created_at).label("max_created"),
        )
        .filter(ChatMessageV2.thread_id.in_(thread_ids))
        .group_by(ChatMessageV2.thread_id)
        .subquery()
    )

    last_messages_rows = (
        db.query(ChatMessageV2, User)
        .outerjoin(User, User.id == ChatMessageV2.sender_id)
        .join(
            latest_msg_subq,
            and_(
                ChatMessageV2.thread_id == latest_msg_subq.c.thread_id,
                ChatMessageV2.created_at == latest_msg_subq.c.max_created,
            ),
        )
        .all()
    )

    last_msg_by_thread: Dict[uuid.UUID, Tuple[ChatMessageV2, Optional[User]]] = {}
    for msg, sender in last_messages_rows:
        # In case of ties (same created_at), just pick one
        if msg.thread_id not in last_msg_by_thread:
            last_msg_by_thread[msg.thread_id] = (msg, sender)

    # --- 3) Unread counts in batch ---
    # We need: for each thread, count messages where sender != current_user
    # AND created_at > last_read_message.created_at (if last_read_message_id is set)
    #
    # Strategy: fetch last_read_message created_at for threads that have one,
    # then compute counts in a single query.

    # Collect last_read_message_ids that are set
    last_read_ids = {}
    for tid, cp in my_participation_by_thread.items():
        if cp.last_read_message_id:
            last_read_ids[tid] = cp.last_read_message_id

    # Fetch created_at for all last-read messages in one query
    last_read_timestamps: Dict[uuid.UUID, datetime] = {}
    if last_read_ids:
        read_msg_ids = list(last_read_ids.values())
        read_msgs = (
            db.query(ChatMessageV2.id, ChatMessageV2.created_at)
            .filter(ChatMessageV2.id.in_(read_msg_ids))
            .all()
        )
        read_msg_map = {m_id: m_created for m_id, m_created in read_msgs}
        for tid, msg_id in last_read_ids.items():
            if msg_id in read_msg_map:
                last_read_timestamps[tid] = read_msg_map[msg_id]

    # Now compute unread counts — we can batch this per-thread with a CASE/UNION
    # approach, but the simplest efficient approach is a single query with grouping.
    # We'll use a Python-side filter on the SQL result to handle per-thread thresholds.
    #
    # For threads WITH a last-read timestamp, we filter created_at > threshold.
    # For threads WITHOUT, we count ALL messages from others.
    #
    # Most efficient: single query grouping by thread_id with the broadest filter,
    # then adjust in Python. But that still scans all messages.
    #
    # Better: two queries — one for threads without last_read (count all from others),
    # one for threads with last_read (count after threshold).
    # Or just one query with conditional — let's use a CASE approach via raw SQL
    # fragments for efficiency.

    unread_by_thread: Dict[uuid.UUID, int] = defaultdict(int)

    # Threads where we have NO last_read (count all messages from others)
    threads_no_read = [tid for tid in thread_ids if tid not in last_read_timestamps and tid in my_participation_by_thread]
    # Threads where we HAVE last_read
    threads_with_read = [tid for tid in thread_ids if tid in last_read_timestamps]

    if threads_no_read:
        rows = (
            db.query(
                ChatMessageV2.thread_id,
                func.count(ChatMessageV2.id),
            )
            .filter(
                ChatMessageV2.thread_id.in_(threads_no_read),
                ChatMessageV2.sender_id != current_user.id,
            )
            .group_by(ChatMessageV2.thread_id)
            .all()
        )
        for tid, cnt in rows:
            unread_by_thread[tid] = cnt

    if threads_with_read:
        # For each thread with a last_read timestamp, we need messages after that
        # timestamp.  We can do this in one query with an OR of conditions, but
        # the cleanest way with SQLAlchemy is a UNION or individual filters.
        # the cleanest way with SQLAlchemy is a UNION or individual filters.
        # Since the number of threads is usually small (<100), we can use a single
        # query with a subquery join.

        # Build a values-like structure: for each thread_id, its threshold timestamp
        # We'll iterate — the overhead is one query per batch, not per thread.
        # Actually, let's do it in one query by joining.
        #
        # Approach: query messages where thread_id in threads_with_read,
        # sender != me, and then in Python filter by timestamp per thread.
        # This is efficient if total messages in these threads is moderate.
        #
        # For very large threads, an individual count per thread would be better,
        # but for typical messenger usage this is fine.

        # Alternative: use a single query with a lateral join or CTE.
        # Simplest correct approach: one aggregation query per threshold grouping.
        # Since thresholds differ per thread, let's query all candidate messages
        # and group/filter in Python.

        candidate_msgs = (
            db.query(
                ChatMessageV2.thread_id,
                ChatMessageV2.created_at,
            )
            .filter(
                ChatMessageV2.thread_id.in_(threads_with_read),
                ChatMessageV2.sender_id != current_user.id,
            )
            .all()
        )

        for tid, msg_created in candidate_msgs:
            threshold = last_read_timestamps.get(tid)
            if threshold and msg_created > threshold:
                unread_by_thread[tid] += 1
            elif not threshold:
                unread_by_thread[tid] += 1

    # --- Assemble ThreadOut objects ---
    result = []
    for thread in threads:
        participants = participants_by_thread.get(thread.id, [])

        # Last message
        last_message = None
        lm_pair = last_msg_by_thread.get(thread.id)
        if lm_pair:
            msg, sender = lm_pair
            forwarded_from = _resolve_forwarded_from(msg, db) if msg.forwarded_from_id else None
            last_message = _build_message_out_with_user(msg, sender, forwarded_from=forwarded_from)

        # Thread name for direct chats
        thread_name = thread.name
        if thread.type == "direct" and not thread_name:
            other_participants = [p for p in participants if p.user_id != str(current_user.id)]
            if other_participants:
                thread_name = other_participants[0].full_name

        result.append(ThreadOut(
            id=str(thread.id),
            type=thread.type,
            name=thread_name,
            avatar_url=thread.avatar_url,
            created_at=thread.created_at.isoformat() if thread.created_at else "",
            updated_at=thread.updated_at.isoformat() if thread.updated_at else "",
            last_message=last_message,
            unread_count=unread_by_thread.get(thread.id, 0),
            participants=participants,
        ))

    return result


# =====================================================
# Endpoints
# =====================================================

@router.get("/threads", response_model=ThreadListResponse)
def list_threads(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get all threads for current user, ordered by last activity."""
    # Find thread IDs where user is a participant
    my_thread_ids = (
        db.query(ChatParticipant.thread_id)
        .filter(ChatParticipant.user_id == current_user.id)
        .subquery()
    )

    threads = (
        db.query(ChatThread)
        .filter(ChatThread.id.in_(db.query(my_thread_ids.c.thread_id)))
        .order_by(ChatThread.updated_at.desc())
        .all()
    )

    # Batch-optimized: 3 queries total instead of 3*N
    result = _batch_build_threads(threads, current_user, db)

    return ThreadListResponse(threads=result)


@router.get("/threads/{thread_id}", response_model=ThreadOut)
def get_thread(
    thread_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get a single thread by ID."""
    tid = uuid.UUID(thread_id)
    _check_is_participant(tid, current_user.id, db)

    thread = db.query(ChatThread).filter(ChatThread.id == tid).first()
    if not thread:
        raise HTTPException(status_code=404, detail="Thread not found")

    return _build_thread_out(thread, current_user, db)


@router.post("/threads", response_model=ThreadOut)
def create_thread(
    payload: CreateThreadRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Create a new direct or group chat."""
    if payload.type not in ("direct", "group"):
        raise HTTPException(status_code=400, detail="type must be 'direct' or 'group'")

    if not payload.participant_ids:
        raise HTTPException(status_code=400, detail="participant_ids is required")

    # For direct chats, check existing thread
    if payload.type == "direct":
        if len(payload.participant_ids) != 1:
            raise HTTPException(status_code=400, detail="Direct chat requires exactly 1 participant_id")

        other_user_id = uuid.UUID(payload.participant_ids[0])

        # Check if direct thread already exists between these two users
        existing = (
            db.query(ChatThread)
            .join(ChatParticipant, ChatParticipant.thread_id == ChatThread.id)
            .filter(
                ChatThread.type == "direct",
                ChatParticipant.user_id == current_user.id,
            )
            .all()
        )

        for t in existing:
            other_cp = (
                db.query(ChatParticipant)
                .filter(
                    ChatParticipant.thread_id == t.id,
                    ChatParticipant.user_id == other_user_id,
                )
                .first()
            )
            if other_cp:
                # Already exists — return it
                return _build_thread_out(t, current_user, db)

    now = datetime.now(timezone.utc)
    thread = ChatThread(
        type=payload.type,
        name=payload.name if payload.type == "group" else None,
        created_by=current_user.id,
        created_at=now,
        updated_at=now,
    )
    db.add(thread)
    db.flush()

    # Add creator as admin
    db.add(ChatParticipant(
        thread_id=thread.id,
        user_id=current_user.id,
        role="admin",
        joined_at=now,
    ))

    # Add other participants
    for uid_str in payload.participant_ids:
        uid = uuid.UUID(uid_str)
        if uid == current_user.id:
            continue
        # Verify user exists
        user_exists = db.query(User).filter(User.id == uid).first()
        if not user_exists:
            raise HTTPException(status_code=404, detail=f"User {uid_str} not found")

        db.add(ChatParticipant(
            thread_id=thread.id,
            user_id=uid,
            role="member",
            joined_at=now,
        ))

    # System message
    db.add(ChatMessageV2(
        thread_id=thread.id,
        sender_id=current_user.id,
        message_type="system",
        text="Чат создан",
        created_at=now,
    ))

    db.commit()
    db.refresh(thread)

    return _build_thread_out(thread, current_user, db)


@router.get("/threads/{thread_id}/messages", response_model=MessagesResponse)
def get_messages(
    thread_id: str,
    limit: int = Query(50, ge=1, le=200),
    before: Optional[str] = Query(None),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get messages in a thread with pagination."""
    tid = uuid.UUID(thread_id)
    cp = _check_is_participant(tid, current_user.id, db)

    q = (
        db.query(ChatMessageV2, User)
        .outerjoin(User, User.id == ChatMessageV2.sender_id)
        .filter(ChatMessageV2.thread_id == tid)
    )

    if before:
        before_msg = db.query(ChatMessageV2).filter(ChatMessageV2.id == uuid.UUID(before)).first()
        if before_msg:
            q = q.filter(ChatMessageV2.created_at < before_msg.created_at)

    rows = q.order_by(ChatMessageV2.created_at.desc()).limit(limit + 1).all()

    has_more = len(rows) > limit
    if has_more:
        rows = rows[:limit]

    # Reverse to chronological order
    rows.reverse()

    # Determine last_read_message created_at for is_read computation
    last_read_created_at = None
    if cp.last_read_message_id:
        last_read_msg = db.query(ChatMessageV2.created_at).filter(
            ChatMessageV2.id == cp.last_read_message_id
        ).first()
        if last_read_msg:
            last_read_created_at = last_read_msg[0]

    # Batch-resolve forwarded_from names
    forwarded_ids = [msg.forwarded_from_id for msg, _ in rows if msg.forwarded_from_id]
    forwarded_names: Dict[uuid.UUID, str] = {}
    if forwarded_ids:
        original_msgs = (
            db.query(ChatMessageV2.id, User)
            .join(User, User.id == ChatMessageV2.sender_id)
            .filter(ChatMessageV2.id.in_(forwarded_ids))
            .all()
        )
        for msg_id, user in original_msgs:
            forwarded_names[msg_id] = user.full_name if user else "Неизвестный"

    result = []
    for msg, sender in rows:
        # A message is "read" by the current user if:
        # - the user sent it themselves, OR
        # - it was created at or before the last_read_message timestamp
        if msg.sender_id == current_user.id:
            is_read = True
        elif last_read_created_at and msg.created_at <= last_read_created_at:
            is_read = True
        else:
            is_read = False

        fwd_name = forwarded_names.get(msg.forwarded_from_id) if msg.forwarded_from_id else None
        result.append(_build_message_out_with_user(msg, sender, is_read=is_read, forwarded_from=fwd_name))

    return MessagesResponse(messages=result, has_more=has_more)


@router.post("/threads/{thread_id}/messages", response_model=MessageOut)
async def send_message(
    thread_id: str,
    payload: SendMessageRequest,
    background_tasks: BackgroundTasks,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Send a message in a thread."""
    tid = uuid.UUID(thread_id)
    _check_is_participant(tid, current_user.id, db)

    # Validate that at least one content field is provided
    has_content = (
        payload.text
        or payload.photo_url
        or payload.voice_url
        or payload.file_url
        or payload.forwarded_from_id
    )
    if not has_content:
        raise HTTPException(status_code=400, detail="Message must have text, photo, voice, file, or be a forward")

    now = datetime.now(timezone.utc)

    # Handle forwarded message
    forwarded_from_id = None
    forwarded_from_name = None
    if payload.forwarded_from_id:
        forwarded_from_id = uuid.UUID(payload.forwarded_from_id)
        original_msg = db.query(ChatMessageV2).filter(ChatMessageV2.id == forwarded_from_id).first()
        if not original_msg:
            raise HTTPException(status_code=404, detail="Original message not found for forwarding")
        original_sender = db.query(User).filter(User.id == original_msg.sender_id).first()
        forwarded_from_name = original_sender.full_name if original_sender else "Неизвестный"
        # If no text/photo/voice/file provided, copy from original
        if not payload.text and not payload.photo_url and not payload.voice_url and not payload.file_url:
            payload.text = original_msg.text
            payload.photo_url = original_msg.photo_url
            payload.voice_url = original_msg.voice_url
            payload.file_url = original_msg.file_url
            payload.file_name = original_msg.file_name
            payload.file_size = int(original_msg.file_size) if original_msg.file_size else None
            payload.message_type = original_msg.message_type
            payload.voice_duration_seconds = float(original_msg.voice_duration_seconds) if original_msg.voice_duration_seconds else None

    msg = ChatMessageV2(
        thread_id=tid,
        sender_id=current_user.id,
        message_type=payload.message_type,
        text=payload.text,
        photo_url=payload.photo_url,
        voice_url=payload.voice_url,
        voice_duration_seconds=payload.voice_duration_seconds,
        file_url=payload.file_url,
        file_name=payload.file_name,
        file_size=payload.file_size,
        forwarded_from_id=forwarded_from_id,
        reply_to_id=uuid.UUID(payload.reply_to_id) if payload.reply_to_id else None,
        created_at=now,
    )
    db.add(msg)

    # Update thread timestamp
    thread = db.query(ChatThread).filter(ChatThread.id == tid).first()
    if thread:
        thread.updated_at = now

    db.commit()
    db.refresh(msg)

    # Build message output for WS broadcast — sender is current_user, already in memory
    msg_out = _build_message_out_with_user(msg, current_user, is_read=True, forwarded_from=forwarded_from_name)

    # WebSocket broadcast (async, non-blocking)
    await notify_new_message(tid, msg_out.dict(), current_user.id)

    # Send push notifications to other participants (background)
    # Batch-fetch participants and their user info in one query
    participant_rows = (
        db.query(ChatParticipant, User)
        .join(User, User.id == ChatParticipant.user_id)
        .filter(
            ChatParticipant.thread_id == tid,
            ChatParticipant.user_id != current_user.id,
        )
        .all()
    )

    for cp, recipient in participant_rows:
        try:
            # Determine message preview
            preview = payload.text or ""
            if payload.message_type == "photo":
                preview = "Фото"
            elif payload.message_type == "voice":
                preview = "Голосовое сообщение"
            elif payload.message_type == "file":
                preview = f"Файл: {payload.file_name or 'документ'}"

            send_chat_message_notification(
                recipient_phone=recipient.phone,
                sender_name=current_user.full_name,
                message_text=preview,
                db=db,
            )
        except Exception:
            pass  # Don't fail message send if push fails

    return msg_out


# =====================================================
# File Upload Endpoint
# =====================================================

@router.post("/upload-file", response_model=FileUploadResponse)
async def upload_messenger_file(
    file: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
):
    """Upload a file for messenger (documents, images, archives, drawings)."""
    if not file.filename:
        raise HTTPException(status_code=400, detail="Filename is required")

    # Check extension
    ext = ""
    if "." in file.filename:
        ext = "." + file.filename.rsplit(".", 1)[-1].lower()

    if ext not in ALLOWED_FILE_EXTENSIONS:
        raise HTTPException(
            status_code=400,
            detail=f"File type '{ext}' is not allowed. Allowed: {', '.join(sorted(ALLOWED_FILE_EXTENSIONS))}",
        )

    # Read file content
    content = await file.read()

    # Check size
    if len(content) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"File too large. Maximum size: {MAX_FILE_SIZE // (1024 * 1024)} MB",
        )

    file_size = len(content)
    original_name = file.filename

    # Upload to S3 using existing storage module
    file_url = await s3_upload_file(content, file.filename, prefix="messenger_files")

    return FileUploadResponse(
        file_url=file_url,
        file_name=original_name,
        file_size=file_size,
    )


# =====================================================
# Delete Message Endpoint
# =====================================================

@router.delete("/threads/{thread_id}/messages/{message_id}", response_model=MessageOut)
async def delete_message(
    thread_id: str,
    message_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Soft-delete a message. Only the sender can delete their own message."""
    tid = uuid.UUID(thread_id)
    mid = uuid.UUID(message_id)

    _check_is_participant(tid, current_user.id, db)

    msg = (
        db.query(ChatMessageV2)
        .filter(ChatMessageV2.id == mid, ChatMessageV2.thread_id == tid)
        .first()
    )

    if not msg:
        raise HTTPException(status_code=404, detail="Message not found")

    if msg.sender_id != current_user.id:
        raise HTTPException(status_code=403, detail="Вы можете удалить только свои сообщения")

    if msg.message_type == "deleted":
        raise HTTPException(status_code=400, detail="Message is already deleted")

    # Soft delete: clear content and mark as deleted
    msg.message_type = "deleted"
    msg.text = None
    msg.photo_url = None
    msg.voice_url = None
    msg.file_url = None
    msg.file_name = None
    msg.file_size = None
    msg.voice_duration_seconds = None

    db.commit()
    db.refresh(msg)

    msg_out = _build_message_out_with_user(msg, current_user, is_read=True)

    # Broadcast deletion via WebSocket
    from .ws_messenger import manager
    await manager.broadcast_to_thread(
        thread_id=tid,
        data={
            "type": "message_deleted",
            "thread_id": str(tid),
            "message_id": str(mid),
            "message": msg_out.dict(),
        },
    )

    return msg_out


# =====================================================
# Search Endpoint
# =====================================================

@router.get("/search", response_model=SearchResponse)
def search_messages(
    q: str = Query(..., min_length=1, max_length=200),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Search messages by text across all threads the user participates in."""
    # Get thread IDs where user is a participant
    my_thread_ids_sq = (
        db.query(ChatParticipant.thread_id)
        .filter(ChatParticipant.user_id == current_user.id)
        .subquery()
    )

    # Search messages with ILIKE
    rows = (
        db.query(ChatMessageV2, User, ChatThread)
        .outerjoin(User, User.id == ChatMessageV2.sender_id)
        .outerjoin(ChatThread, ChatThread.id == ChatMessageV2.thread_id)
        .filter(
            ChatMessageV2.thread_id.in_(db.query(my_thread_ids_sq.c.thread_id)),
            ChatMessageV2.text.ilike(f"%{q}%"),
            ChatMessageV2.message_type != "deleted",
        )
        .order_by(ChatMessageV2.created_at.desc())
        .limit(50)
        .all()
    )

    results = []
    for msg, sender, thread in rows:
        # Determine thread name for display
        thread_name = thread.name if thread else None
        if thread and thread.type == "direct" and not thread_name:
            # For direct chats, use the other participant's name
            other = (
                db.query(User)
                .join(ChatParticipant, ChatParticipant.user_id == User.id)
                .filter(
                    ChatParticipant.thread_id == thread.id,
                    ChatParticipant.user_id != current_user.id,
                )
                .first()
            )
            if other:
                thread_name = other.full_name

        results.append(SearchMessageOut(
            id=str(msg.id),
            thread_id=str(msg.thread_id),
            thread_name=thread_name,
            sender_id=str(msg.sender_id),
            sender_name=sender.full_name if sender else "Неизвестный",
            message_type=msg.message_type or "text",
            text=msg.text,
            created_at=msg.created_at.isoformat() if msg.created_at else "",
        ))

    return SearchResponse(messages=results)


# =====================================================
# Remaining Endpoints (unchanged)
# =====================================================

@router.post("/threads/{thread_id}/read")
def mark_read(
    thread_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Mark all messages in thread as read for current user."""
    tid = uuid.UUID(thread_id)
    cp = _check_is_participant(tid, current_user.id, db)

    last_msg = (
        db.query(ChatMessageV2)
        .filter(ChatMessageV2.thread_id == tid)
        .order_by(ChatMessageV2.created_at.desc())
        .first()
    )

    if last_msg:
        cp.last_read_message_id = last_msg.id
        db.commit()

    return {"status": "ok"}


@router.put("/threads/{thread_id}")
async def update_thread(
    thread_id: str,
    payload: UpdateThreadRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Update thread name (group chats only)."""
    tid = uuid.UUID(thread_id)
    cp = _check_is_participant(tid, current_user.id, db)

    thread = db.query(ChatThread).filter(ChatThread.id == tid).first()
    if not thread:
        raise HTTPException(status_code=404, detail="Thread not found")

    if thread.type != "group":
        raise HTTPException(status_code=400, detail="Can only rename group chats")

    if cp.role != "admin":
        raise HTTPException(status_code=403, detail="Only admins can rename the group")

    if payload.name is not None:
        thread.name = payload.name
        thread.updated_at = datetime.now(timezone.utc)

    db.commit()
    await notify_thread_updated(tid)
    return _build_thread_out(thread, current_user, db)


@router.post("/threads/{thread_id}/members")
async def add_members(
    thread_id: str,
    payload: AddMembersRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Add members to a group chat."""
    tid = uuid.UUID(thread_id)
    cp = _check_is_participant(tid, current_user.id, db)

    thread = db.query(ChatThread).filter(ChatThread.id == tid).first()
    if not thread or thread.type != "group":
        raise HTTPException(status_code=400, detail="Can only add members to group chats")

    now = datetime.now(timezone.utc)
    added = []

    for uid_str in payload.user_ids:
        uid = uuid.UUID(uid_str)
        existing = (
            db.query(ChatParticipant)
            .filter(ChatParticipant.thread_id == tid, ChatParticipant.user_id == uid)
            .first()
        )
        if existing:
            continue

        user = db.query(User).filter(User.id == uid).first()
        if not user:
            continue

        db.add(ChatParticipant(
            thread_id=tid,
            user_id=uid,
            role="member",
            joined_at=now,
        ))
        added.append(user.full_name)

    if added:
        # System message
        db.add(ChatMessageV2(
            thread_id=tid,
            sender_id=current_user.id,
            message_type="system",
            text=f"{', '.join(added)} добавлен(ы) в чат",
            created_at=now,
        ))
        thread.updated_at = now

    db.commit()
    await notify_thread_updated(tid)

    return _build_thread_out(thread, current_user, db)


@router.delete("/threads/{thread_id}/members/{user_id}")
async def remove_member(
    thread_id: str,
    user_id: str,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Remove a member from a group chat."""
    tid = uuid.UUID(thread_id)
    cp = _check_is_participant(tid, current_user.id, db)

    thread = db.query(ChatThread).filter(ChatThread.id == tid).first()
    if not thread or thread.type != "group":
        raise HTTPException(status_code=400, detail="Can only remove members from group chats")

    target_uid = uuid.UUID(user_id)

    # Only admin or self can remove
    if cp.role != "admin" and target_uid != current_user.id:
        raise HTTPException(status_code=403, detail="Only admins can remove members")

    target_cp = (
        db.query(ChatParticipant)
        .filter(ChatParticipant.thread_id == tid, ChatParticipant.user_id == target_uid)
        .first()
    )
    if target_cp:
        removed_user = db.query(User).filter(User.id == target_uid).first()
        db.delete(target_cp)

        now = datetime.now(timezone.utc)
        action = "покинул(а) чат" if target_uid == current_user.id else "удалён(а) из чата"
        db.add(ChatMessageV2(
            thread_id=tid,
            sender_id=current_user.id,
            message_type="system",
            text=f"{removed_user.full_name if removed_user else 'Пользователь'} {action}",
            created_at=now,
        ))
        thread.updated_at = now
        db.commit()
        await notify_thread_updated(tid)

    return {"status": "ok"}


@router.get("/contacts", response_model=List[ContactOut])
def get_contacts(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """
    Get available contacts based on user role:
    - Installer: foreman + brigade mates
    - Foreman: own installers + own curator(s) + other foremen in same curator scope
    - Curator: all foremen + all installers
    """
    contacts: List[ContactOut] = []
    seen_ids = set()

    def add_user(u: User):
        if u.id == current_user.id or u.id in seen_ids:
            return
        seen_ids.add(u.id)
        contacts.append(ContactOut(
            id=str(u.id),
            full_name=u.full_name,
            phone=u.phone,
            role=u.role,
        ))

    if current_user.role == "installer":
        # My foreman
        membership = (
            db.query(ForemanMembership)
            .filter(
                ForemanMembership.installer_user_id == current_user.id,
                ForemanMembership.status == "active",
            )
            .first()
        )
        if membership:
            foreman = db.query(User).filter(User.id == membership.foreman_user_id).first()
            if foreman:
                add_user(foreman)

            # Brigade mates
            mates = (
                db.query(ForemanMembership)
                .filter(
                    ForemanMembership.foreman_user_id == membership.foreman_user_id,
                    ForemanMembership.status == "active",
                    ForemanMembership.installer_user_id != current_user.id,
                )
                .all()
            )
            for m in mates:
                u = db.query(User).filter(User.id == m.installer_user_id).first()
                if u:
                    add_user(u)

    elif current_user.role == "foreman":
        # My installers
        memberships = (
            db.query(ForemanMembership)
            .filter(
                ForemanMembership.foreman_user_id == current_user.id,
                ForemanMembership.status == "active",
            )
            .all()
        )
        for m in memberships:
            u = db.query(User).filter(User.id == m.installer_user_id).first()
            if u:
                add_user(u)

        # Curators (all curators for now, since there's no curator_id on foreman)
        curators = db.query(User).filter(User.role == "curator").all()
        for c in curators:
            add_user(c)

        # Other foremen visible to same curator (simplified: all foremen)
        foremen = db.query(User).filter(User.role == "foreman", User.id != current_user.id).all()
        for f in foremen:
            add_user(f)

    elif current_user.role == "curator":
        # All users
        all_users = db.query(User).filter(User.id != current_user.id).all()
        for u in all_users:
            add_user(u)

    return contacts
