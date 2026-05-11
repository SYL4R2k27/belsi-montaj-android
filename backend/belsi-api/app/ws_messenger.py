# app/ws_messenger.py
"""
WebSocket module for real-time messenger.

Events (server → client):
  - new_message: { type, thread_id, message: MessageOut }
  - thread_updated: { type, thread_id }
  - typing: { type, thread_id, user_id, user_name }
  - read: { type, thread_id, user_id }

Events (client → server):
  - typing: { type: "typing", thread_id }
  - read: { type: "read", thread_id }

Connection: WS /ws/messenger?token=demo-token-...
"""

from __future__ import annotations

import asyncio
import json
import logging
import uuid
from datetime import datetime, timezone
from typing import Dict, Set, Optional

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends, Query
from sqlalchemy.orm import Session

from .db import get_db, SessionLocal
from .models import User, ChatParticipant

logger = logging.getLogger("ws_messenger")

router = APIRouter()


# =====================================================
# Connection Manager
# =====================================================

class ConnectionManager:
    """Manages active WebSocket connections per user."""

    def __init__(self):
        # user_id → set of WebSocket connections (user can have multiple devices)
        self._connections: Dict[uuid.UUID, Set[WebSocket]] = {}
        # websocket → user_id (reverse lookup)
        self._ws_to_user: Dict[WebSocket, uuid.UUID] = {}

    async def connect(self, ws: WebSocket, user_id: uuid.UUID):
        await ws.accept()
        if user_id not in self._connections:
            self._connections[user_id] = set()
        self._connections[user_id].add(ws)
        self._ws_to_user[ws] = user_id
        logger.info(f"WS connected: user={user_id}, total={self.total_connections}")

    def disconnect(self, ws: WebSocket):
        user_id = self._ws_to_user.pop(ws, None)
        if user_id and user_id in self._connections:
            self._connections[user_id].discard(ws)
            if not self._connections[user_id]:
                del self._connections[user_id]
        logger.info(f"WS disconnected: user={user_id}, total={self.total_connections}")

    async def send_to_user(self, user_id: uuid.UUID, data: dict):
        """Send JSON message to all connections of a user."""
        connections = self._connections.get(user_id, set()).copy()
        dead = []
        for ws in connections:
            try:
                await ws.send_json(data)
            except Exception:
                dead.append(ws)
        for ws in dead:
            self.disconnect(ws)

    async def send_to_users(self, user_ids: list[uuid.UUID], data: dict):
        """Send JSON message to multiple users."""
        for uid in user_ids:
            await self.send_to_user(uid, data)

    async def broadcast_to_thread(self, thread_id: uuid.UUID, data: dict, exclude_user: uuid.UUID = None):
        """Send to all participants of a thread."""
        # Fetch participants synchronously first, then send async
        db = SessionLocal()
        try:
            participant_ids = [
                uid for (uid,) in
                db.query(ChatParticipant.user_id)
                .filter(ChatParticipant.thread_id == thread_id)
                .all()
            ]
        finally:
            db.close()
        # Now send without holding DB connection
        for uid in participant_ids:
            if exclude_user and uid == exclude_user:
                continue
            await self.send_to_user(uid, data)

    def is_online(self, user_id: uuid.UUID) -> bool:
        return user_id in self._connections and len(self._connections[user_id]) > 0

    @property
    def total_connections(self) -> int:
        return sum(len(s) for s in self._connections.values())


# Singleton
manager = ConnectionManager()


# =====================================================
# Auth helper for WebSocket
# =====================================================

def authenticate_ws(token: str, db: Session) -> Optional[User]:
    """Validate token (JWT or demo-token) and return User, or None."""
    if not token:
        return None
    # 1) Legacy demo-token
    if token.startswith("demo-token-"):
        phone = token[len("demo-token-"):]
        return db.query(User).filter(User.phone == phone).first()
    # 2) JWT token
    try:
        from .auth import decode_jwt_token
        phone = decode_jwt_token(token)
        return db.query(User).filter(User.phone == phone).first()
    except Exception:
        return None


# =====================================================
# Public API for messenger.py to notify WS clients
# =====================================================

async def notify_new_message(thread_id: uuid.UUID, message_data: dict, sender_id: uuid.UUID):
    """Called from messenger.py after saving a message to DB."""
    await manager.broadcast_to_thread(
        thread_id=thread_id,
        data={
            "type": "new_message",
            "thread_id": str(thread_id),
            "message": message_data,
        },
        exclude_user=None,  # send to everyone including sender (for multi-device sync)
    )


async def notify_thread_updated(thread_id: uuid.UUID):
    """Called when thread is updated (new member, renamed, etc)."""
    await manager.broadcast_to_thread(
        thread_id=thread_id,
        data={
            "type": "thread_updated",
            "thread_id": str(thread_id),
        },
    )


# =====================================================
# WebSocket endpoint
# =====================================================

@router.websocket("/ws/messenger")
async def websocket_messenger(ws: WebSocket, token: str = Query(...)):
    db = SessionLocal()
    try:
        user = authenticate_ws(token, db)
    finally:
        db.close()

    if not user:
        await ws.close(code=4001, reason="Unauthorized")
        return

    user_id = user.id
    await manager.connect(ws, user_id)

    # Update last_seen on connect
    try:
        db2 = SessionLocal()
        db2.execute(sa_text('UPDATE users SET last_seen = NOW() WHERE id = :uid'), {'uid': str(user_id)})
        db2.commit()
        db2.close()
    except Exception:
        pass

    try:
        while True:
            raw = await ws.receive_text()
            try:
                data = json.loads(raw)
            except json.JSONDecodeError:
                continue

            event_type = data.get("type")

            if event_type == "typing":
                thread_id_str = data.get("thread_id")
                if thread_id_str:
                    tid = uuid.UUID(thread_id_str)
                    await manager.broadcast_to_thread(
                        thread_id=tid,
                        data={
                            "type": "typing",
                            "thread_id": thread_id_str,
                            "user_id": str(user_id),
                            "user_name": user.full_name or user.phone,
                        },
                        exclude_user=user_id,
                    )

            elif event_type == "read":
                thread_id_str = data.get("thread_id")
                if thread_id_str:
                    tid = uuid.UUID(thread_id_str)
                    await manager.broadcast_to_thread(
                        thread_id=tid,
                        data={
                            "type": "read",
                            "thread_id": thread_id_str,
                            "user_id": str(user_id),
                        },
                        exclude_user=user_id,
                    )

            elif event_type == "ping":
                await ws.send_json({"type": "pong"})

    except WebSocketDisconnect:
        manager.disconnect(ws)
    except Exception as e:
        logger.error(f"WS error for user {user_id}: {e}")
        manager.disconnect(ws)
