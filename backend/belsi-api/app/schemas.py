from datetime import datetime
from typing import Optional, List
from pydantic import BaseModel
from .models import SupportStatus, SupportSenderRole
from uuid import UUID as PyUUID


class SupportMessageOut(BaseModel):
    id: PyUUID
    ticket_id: PyUUID
    sender_role: SupportSenderRole
    text: str
    photo_url: Optional[str] = None
    created_at: datetime

    class Config:
        from_attributes = True


class SupportTicketOut(BaseModel):
    id: PyUUID
    category: Optional[str]
    status: SupportStatus
    created_at: datetime
    updated_at: datetime
    # короткая сводка
    last_message: Optional[SupportMessageOut] = None

    class Config:
        from_attributes = True


class SupportTicketDetailOut(BaseModel):
    id: PyUUID
    category: Optional[str]
    status: SupportStatus
    created_at: datetime
    updated_at: datetime
    messages: List[SupportMessageOut]

    class Config:
        from_attributes = True


class SupportTicketCreate(BaseModel):
    category: Optional[str] = None
    text: str


class SupportMessageCreate(BaseModel):
    photo_url: Optional[str] = None
    text: str


# Chat photo upload response
class ChatPhotoUploadResponse(BaseModel):
    photo_url: str


# Chat voice upload response
class ChatVoiceUploadResponse(BaseModel):
    voice_url: str
    duration_seconds: float
