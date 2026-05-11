"""
Push notification service using Firebase Admin SDK (FCM v1 API)
"""
from __future__ import annotations
import logging
from typing import Optional, Dict, Any
from uuid import UUID

import firebase_admin
from firebase_admin import credentials, messaging

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/push", tags=["push"])

# Firebase initialization
_firebase_initialized = False

def init_firebase():
    global _firebase_initialized
    if _firebase_initialized:
        return True
    
    try:
        cred = credentials.Certificate("/opt/belsi-api/config/firebase-service-account.json")
        firebase_admin.initialize_app(cred)
        _firebase_initialized = True
        logger.info("Firebase Admin SDK initialized successfully")
        return True
    except Exception as e:
        logger.error(f"Failed to initialize Firebase: {e}")
        return False

# Initialize on module load
init_firebase()


class RegisterTokenRequest(BaseModel):
    fcm_token: str


def send_fcm_notification(
    token: str,
    title: str,
    body: str,
    data: Optional[Dict[str, str]] = None
) -> bool:
    """Send push notification via Firebase Admin SDK"""
    if not _firebase_initialized:
        logger.warning("Firebase not initialized, skipping push")
        return False
    
    if not token:
        logger.warning("No FCM token provided")
        return False
    
    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title=title,
                body=body
            ),
            data=data or {},
            token=token
        )
        response = messaging.send(message)
        logger.info(f"Push sent successfully: {response}")
        return True
    except messaging.UnregisteredError:
        logger.warning(f"FCM token is unregistered: {token[:20]}...")
        return False
    except Exception as e:
        logger.error(f"Error sending push: {e}")
        return False


def send_data_message(
    token: str,
    data: Dict[str, str]
) -> bool:
    """Send data-only message (for background processing)"""
    if not _firebase_initialized:
        return False
    
    if not token:
        return False
    
    try:
        message = messaging.Message(
            data=data,
            token=token,
            android=messaging.AndroidConfig(
                priority="high"
            )
        )
        response = messaging.send(message)
        logger.info(f"Data message sent: {response}")
        return True
    except Exception as e:
        logger.error(f"Error sending data message: {e}")
        return False


def send_otp_push(phone: str, code: str, db: Session) -> bool:
    """Send OTP code via push notification"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE phone = :phone"),
        {"phone": phone}
    ).first()
    
    if not user or not user.fcm_token:
        logger.info(f"No FCM token for phone {phone}")
        return False
    
    return send_data_message(
        token=user.fcm_token,
        data={
            "type": "otp",
            "code": code,
            "phone": phone
        }
    )


def send_task_notification(
    user_id: UUID,
    task_id: str,
    task_title: str,
    assigned_by_name: str,
    priority: str,
    description: str,
    db: Session
) -> bool:
    """Send task assignment notification"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE id = :id"),
        {"id": str(user_id)}
    ).first()
    
    if not user or not user.fcm_token:
        return False
    
    return send_data_message(
        token=user.fcm_token,
        data={
            "type": "task_assigned",
            "task_id": task_id,
            "task_title": task_title,
            "assigned_by": assigned_by_name,
            "priority": priority,
            "description": description or ""
        }
    )


def send_chat_message_notification(
    user_id: UUID,
    ticket_id: str,
    sender_name: str,
    sender_phone: str,
    message_text: str,
    is_curator: bool,
    db: Session
) -> bool:
    """Send chat message notification"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE id = :id"),
        {"id": str(user_id)}
    ).first()
    
    if not user or not user.fcm_token:
        return False
    
    return send_data_message(
        token=user.fcm_token,
        data={
            "type": "chat_message",
            "ticket_id": ticket_id,
            "sender_name": sender_name,
            "sender_phone": sender_phone,
            "message": message_text[:200],
            "is_curator": "true" if is_curator else "false"
        }
    )


def send_support_reply_notification(
    user_id: UUID,
    ticket_id: str,
    ticket_subject: str,
    message_text: str,
    db: Session
) -> bool:
    """Send support ticket reply notification"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE id = :id"),
        {"id": str(user_id)}
    ).first()
    
    if not user or not user.fcm_token:
        return False
    
    return send_data_message(
        token=user.fcm_token,
        data={
            "type": "support_reply",
            "ticket_id": ticket_id,
            "ticket_subject": ticket_subject,
            "message": message_text[:200]
        }
    )


def send_shift_reminder(user_id: UUID, message_text: str, db: Session) -> bool:
    """Send shift reminder"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE id = :id"),
        {"id": str(user_id)}
    ).first()
    
    if not user or not user.fcm_token:
        return False
    
    return send_data_message(
        token=user.fcm_token,
        data={
            "type": "shift_reminder",
            "message": message_text
        }
    )


def send_photo_reminder(user_id: UUID, message_text: str, db: Session) -> bool:
    """Send photo reminder"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE id = :id"),
        {"id": str(user_id)}
    ).first()
    
    if not user or not user.fcm_token:
        return False
    
    return send_data_message(
        token=user.fcm_token,
        data={
            "type": "photo_reminder",
            "message": message_text
        }
    )


@router.post("/register")
def register_fcm_token(
    payload: RegisterTokenRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Register FCM token for current user"""
    db.execute(
        text("UPDATE users SET fcm_token = :token WHERE id = :id"),
        {"token": payload.fcm_token, "id": str(current_user.id)}
    )
    db.commit()
    
    logger.info(f"FCM token registered for user {current_user.id}")
    return {"status": "ok"}


@router.delete("/unregister")
def unregister_fcm_token(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Remove FCM token for current user (on logout)"""
    db.execute(
        text("UPDATE users SET fcm_token = NULL WHERE id = :id"),
        {"id": str(current_user.id)}
    )
    db.commit()
    
    return {"status": "ok"}


@router.post("/test")
def test_push_notification(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user)
):
    """Test push notification for current user"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE id = :id"),
        {"id": str(current_user.id)}
    ).first()
    
    if not user or not user.fcm_token:
        raise HTTPException(status_code=400, detail="No FCM token registered")
    
    success = send_fcm_notification(
        token=user.fcm_token,
        title="BELSI.Work Test",
        body="Push-уведомления работают!",
        data={"type": "test"}
    )
    
    if success:
        return {"status": "ok", "message": "Test notification sent"}
    else:
        raise HTTPException(status_code=500, detail="Failed to send test notification")


def send_task_status_notification(
    user_id: UUID,
    task_title: str,
    new_status: str,
    changed_by_name: str,
    db: Session
) -> bool:
    """Send task status change notification to creator"""
    user = db.execute(
        text("SELECT fcm_token FROM users WHERE id = :id"),
        {"id": str(user_id)}
    ).first()
    
    if not user or not user.fcm_token:
        return False
    
    status_text = {
        "new": "Новая",
        "in_progress": "В работе",
        "done": "Выполнена",
        "cancelled": "Отменена"
    }.get(new_status, new_status)
    
    return send_data_message(
        token=user.fcm_token,
        data={
            "type": "task_status_changed",
            "task_title": task_title,
            "new_status": new_status,
            "status_text": status_text,
            "changed_by": changed_by_name
        }
    )
