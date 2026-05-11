from datetime import datetime
from pathlib import Path
import uuid
from typing import List, Optional
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException
from fastapi import UploadFile, File
from pydantic import BaseModel
from sqlalchemy.orm import Session

from .auth import get_current_user
from .db import get_db
from .models import SupportMessage, SupportSenderRole, SupportStatus, SupportTicket, User

router = APIRouter(prefix="/support", tags=["support"])


def map_sender_role(user_role: str) -> SupportSenderRole:
    r = (user_role or "").lower()
    if r == "installer":
        return SupportSenderRole.USER
    if r == "foreman":
        return SupportSenderRole.FOREMAN
    if r == "curator":
        return SupportSenderRole.CURATOR
    return SupportSenderRole.SYSTEM


# ---- Schemas ----
class TicketCreateIn(BaseModel):
    title: str
    category: str = "general"
    text: str
    photo_url: Optional[str] = None


class TicketOut(BaseModel):
    id: UUID
    user_id: UUID
    title: str
    category: str
    status: str
    created_at: datetime
    updated_at: datetime

    class Config:
        from_attributes = True


class MessageOut(BaseModel):
    id: UUID
    ticket_id: UUID
    sender_role: SupportSenderRole
    sender_user_id: Optional[UUID] = None
    text: str
    is_internal: bool
    created_at: datetime

    class Config:
        from_attributes = True


class TicketWithMessagesOut(BaseModel):
    ticket: TicketOut
    messages: List[MessageOut]


class ReplyIn(BaseModel):
    text: str
    is_internal: bool = False
    photo_url: Optional[str] = None


@router.post("/tickets", response_model=TicketWithMessagesOut)
def create_ticket(
    payload: TicketCreateIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    title = (payload.title or "").strip()
    text = (payload.text or "").strip()
    if not title:
        raise HTTPException(status_code=400, detail="title is required")
    if not text:
        raise HTTPException(status_code=400, detail="text is required")

    ticket = SupportTicket(
        user_id=current_user.id,
        title=title,
        category=payload.category,
        status="open",
        foreman_id=None,
        curator_id=None,
        meta={"title": title},
    )
    db.add(ticket)
    db.flush()  # чтобы ticket.id появился

    msg = SupportMessage(
        ticket_id=ticket.id,
        sender_role=map_sender_role(current_user.role),
        sender_user_id=current_user.id,
        text=text,
        photo_url=payload.photo_url,
        is_internal=False,
    )
    db.add(msg)

    db.commit()
    db.refresh(ticket)

    messages = (
        db.query(SupportMessage)
        .filter(SupportMessage.ticket_id == ticket.id)
        .order_by(SupportMessage.created_at.asc())
        .all()
    )
    return {"ticket": ticket, "messages": messages}


@router.get("/tickets", response_model=List[TicketOut])
def list_tickets(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    q = db.query(SupportTicket).order_by(SupportTicket.created_at.desc())

    # Куратор видит все, остальные — только свои
    if (current_user.role or "").lower() != "curator":
        q = q.filter(SupportTicket.user_id == current_user.id)

    return q.all()


@router.get("/tickets/{ticket_id}", response_model=TicketWithMessagesOut)
def get_ticket(
    ticket_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    ticket = db.query(SupportTicket).filter(SupportTicket.id == ticket_id).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    if (current_user.role or "").lower() != "curator" and ticket.user_id != current_user.id:
        raise HTTPException(status_code=403, detail="Forbidden")

    messages = (
        db.query(SupportMessage)
        .filter(SupportMessage.ticket_id == ticket_id)
        .order_by(SupportMessage.created_at.asc())
        .all()
    )
    return {"ticket": ticket, "messages": messages}


@router.post("/tickets/{ticket_id}/reply", response_model=MessageOut)
def reply_ticket(
    ticket_id: UUID,
    payload: ReplyIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    ticket = db.query(SupportTicket).filter(SupportTicket.id == ticket_id).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    # ответить может владелец тикета или куратор
    if (current_user.role or "").lower() != "curator" and ticket.user_id != current_user.id:
        raise HTTPException(status_code=403, detail="Forbidden")

    text = (payload.text or "").strip()
    if not text:
        raise HTTPException(status_code=400, detail="text is required")

    msg = SupportMessage(
        ticket_id=ticket_id,
        sender_role=map_sender_role(current_user.role),
        sender_user_id=current_user.id,
        text=text,
        is_internal=bool(payload.is_internal),
        photo_url=payload.photo_url,
    )
    db.add(msg)
    db.commit()
    db.refresh(msg)
    return msg


# ============================================
# Chat Photo Upload Configuration
# ============================================
UPLOAD_DIR = Path("/opt/belsi-api/uploads/chat")
MAX_FILE_SIZE = 10 * 1024 * 1024  # 10MB
ALLOWED_CONTENT_TYPES = ["image/jpeg", "image/png", "image/jpg"]
BASE_URL = "https://api.belsi.ru"


# ============================================
# Chat Photo Upload Endpoint
# ============================================
@router.post("/chat/upload-photo")
async def upload_chat_photo(
    photo: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: Session = Depends(get_db)
):
    """
    Upload a photo for chat message attachment
    
    - Max file size: 10MB
    - Allowed formats: JPEG, PNG
    """
    from .schemas import ChatPhotoUploadResponse
    
    # Validate content type
    if photo.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid file type. Allowed: {', '.join(ALLOWED_CONTENT_TYPES)}"
        )
    
    # Read and validate size
    contents = await photo.read()
    if len(contents) > MAX_FILE_SIZE:
        raise HTTPException(
            status_code=400,
            detail=f"File too large. Max size: {MAX_FILE_SIZE / 1024 / 1024}MB"
        )
    
    # Generate unique filename
    file_ext = photo.filename.split('.')[-1].lower() if photo.filename else 'jpg'
    if file_ext not in ['jpg', 'jpeg', 'png']:
        file_ext = 'jpg'
    
    unique_filename = f"chat_{uuid.uuid4()}.{file_ext}"
    
    # Ensure directory exists
    UPLOAD_DIR.mkdir(parents=True, exist_ok=True)
    
    # Save file
    file_path = UPLOAD_DIR / unique_filename
    try:
        with open(file_path, "wb") as f:
            f.write(contents)
    except Exception as e:
        raise HTTPException(status_code=500, detail="Ошибка сохранения файла")
    
    # Return public URL
    photo_url = f"{BASE_URL}/uploads/chat/{unique_filename}"
    
    return ChatPhotoUploadResponse(photo_url=photo_url)


# ============================================
# Additional endpoints for Android compatibility
# ============================================

@router.get("/tickets/{ticket_id}/messages", response_model=List[MessageOut])
def get_ticket_messages(
    ticket_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Get messages for a ticket (Android compatibility endpoint)"""
    ticket = db.query(SupportTicket).filter(SupportTicket.id == ticket_id).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    if (current_user.role or "").lower() != "curator" and ticket.user_id != current_user.id:
        raise HTTPException(status_code=403, detail="Forbidden")

    messages = (
        db.query(SupportMessage)
        .filter(SupportMessage.ticket_id == ticket_id)
        .order_by(SupportMessage.created_at.asc())
        .all()
    )
    return messages


@router.post("/tickets/{ticket_id}/messages", response_model=MessageOut)
def send_ticket_message(
    ticket_id: UUID,
    payload: ReplyIn,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Send a message to a ticket (Android compatibility endpoint, alias for /reply)"""
    return reply_ticket(ticket_id, payload, db, current_user)


@router.put("/tickets/{ticket_id}/close", response_model=TicketOut)
def close_ticket(
    ticket_id: UUID,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Close a ticket"""
    ticket = db.query(SupportTicket).filter(SupportTicket.id == ticket_id).first()
    if not ticket:
        raise HTTPException(status_code=404, detail="Ticket not found")

    if (current_user.role or "").lower() != "curator" and ticket.user_id != current_user.id:
        raise HTTPException(status_code=403, detail="Forbidden")

    ticket.status = "closed"
    ticket.updated_at = datetime.utcnow()
    db.commit()
    db.refresh(ticket)
    return ticket
