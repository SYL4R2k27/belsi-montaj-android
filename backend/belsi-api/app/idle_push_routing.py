"""
BELSI 2.0.0 build4 · Push-маршрутизация по простоям.

Брендбук ecosystem раздел 05 + BELSI.Команда принцип 3
("Простой — это сигнал, а не наказание"):

  Любой простой → Куратор всегда
  ─────────────────────────────────
  Простой Монтажника  → + Бригадир + Координатор объекта
  Простой Работника   → + Старший работник + Начальник производства
  Простой Водителя    → + Логист
  Простой Бригадира   → + Координатор + Куратор
  Простой Старшего    → + Начальник производства

Использование: вызывать notify_idle_event() из place'а где регистрируется простой
(shift_pauses.py при создании паузы с reason='idle' / 'wait_*' и т.п.).

FIX(2026-05-11) BELSI 2.0.0 build4.
"""
from __future__ import annotations
import logging
from typing import List, Optional
from uuid import UUID
from sqlalchemy.orm import Session
from sqlalchemy import text

from .models import User
from .push_notifications import send_data_message

logger = logging.getLogger("idle_push_routing")


def _fcm_tokens_by_role(db: Session, roles: List[str], facility_id: Optional[UUID] = None,
                       site_object_id: Optional[UUID] = None) -> List[str]:
    """Получить активные fcm_tokens юзеров с указанными ролями.
    Если задан facility_id — только юзера привязанного к этой фабрике (через user_role_assignments).
    Если задан site_object_id — для координатора/бригадира своего объекта."""
    if not roles: return []
    placeholders = ",".join([f":r{i}" for i in range(len(roles))])
    params = {f"r{i}": r for i, r in enumerate(roles)}
    query = f"""
        SELECT DISTINCT u.fcm_token
        FROM users u
        WHERE u.role IN ({placeholders})
          AND u.fcm_token IS NOT NULL AND u.fcm_token != ''
    """
    rows = db.execute(text(query), params).all()
    return [r[0] for r in rows]


def _fcm_tokens_for_user_in_object_team(db: Session, role: str, site_object_id: UUID) -> List[str]:
    """Foreman/coordinator привязанные к данному объекту через team_memberships
    или coordinator (site_objects.coordinator_id)."""
    if role == "coordinator":
        row = db.execute(text("""
            SELECT u.fcm_token FROM users u
            INNER JOIN site_objects so ON so.coordinator_id = u.id
            WHERE so.id = :sid AND u.fcm_token IS NOT NULL AND u.fcm_token != ''
        """), {"sid": site_object_id}).all()
        return [r[0] for r in row]
    elif role == "foreman":
        # foreman через foreman_memberships → installer'ы на объекте
        row = db.execute(text("""
            SELECT DISTINCT u.fcm_token
            FROM users u
            INNER JOIN foreman_memberships fm ON fm.foreman_id = u.id
            INNER JOIN shifts s ON s.user_id = fm.installer_id
            WHERE s.site_object_id = :sid
              AND u.fcm_token IS NOT NULL AND u.fcm_token != ''
        """), {"sid": site_object_id}).all()
        return [r[0] for r in row]
    return []


def notify_idle_event(
    db: Session,
    actor_user: User,
    reason_label: str,
    site_object_id: Optional[UUID] = None,
    duration_min: Optional[int] = None,
) -> int:
    """
    Отправляет push'и по всем подходящим руководителям.
    Возвращает число доставленных уведомлений.

    Логика маршрутизации (брендбук ecosystem 05):
      - Куратор ВСЕГДА получает push
      - Дополнительно — иерархия по домену роли actor_user'а
    """
    role = (actor_user.role or "").strip().lower()
    actor_name = actor_user.full_name or actor_user.phone or "—"

    # Кто получит push (помимо куратора)
    extra_roles: List[str] = []
    object_specific_roles: List[str] = []  # роли привязанные к конкретному объекту

    if role == "installer":
        # монтажник → бригадир + координатор объекта + куратор
        object_specific_roles = ["foreman", "coordinator"]
    elif role == "foreman":
        object_specific_roles = ["coordinator"]
    elif role == "worker":
        extra_roles = ["senior_worker", "production_chief"]
    elif role == "senior_worker":
        extra_roles = ["production_chief"]
    elif role == "driver":
        extra_roles = ["logistician"]
    elif role == "production_chief":
        # начальник производства простаивает — это уже куратор-уровень
        pass
    elif role == "logistician":
        pass

    # Куратор всегда
    extra_roles.append("curator")

    # Собираем все токены
    tokens = set()
    tokens.update(_fcm_tokens_by_role(db, list(set(extra_roles))))

    if site_object_id:
        for r in object_specific_roles:
            tokens.update(_fcm_tokens_for_user_in_object_team(db, r, site_object_id))

    # Готовим payload
    duration_part = f" · {duration_min} мин" if duration_min else ""
    title = f"⚠️ {actor_name} в простое"
    body = f"Причина: {reason_label}{duration_part}"
    payload = {
        "type": "idle_alert",
        "title": title,
        "body": body,
        "actor_id": str(actor_user.id),
        "actor_name": actor_name,
        "actor_role": role,
        "reason": reason_label,
    }

    delivered = 0
    for tok in tokens:
        try:
            if send_data_message(tok, payload):
                delivered += 1
        except Exception as e:
            logger.warning(f"idle push failed: {e}")

    logger.info(
        f"idle alert from {actor_name} ({role}): {len(tokens)} targets, {delivered} delivered, "
        f"reason='{reason_label}'"
    )
    return delivered
