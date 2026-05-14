"""
BELSI 2.0.0 build4 · CAPABILITY MATRIX — единый источник правды.

Брендбук ecosystem раздел 12 + driver-integration раздел "Матрица прав":
все права роль×действие в одном месте. Используется:
  - Backend для авторизации (require_capability)
  - Endpoint /capabilities для клиента (динамическая отрисовка UI)

Формат: dict[capability_code] = set(role_codes) — какие роли имеют это право.

FIX(2026-05-11) BELSI 2.0.0 build4.
"""
from __future__ import annotations
from typing import Dict, Set, List
from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import text

from .db import get_db
from .auth import get_current_user
from .models import User


# ═══════════════════════════════════════════════════════════════════════════
# CAPABILITY MATRIX (из брендбука driver-integration "Матрица прав")
# ═══════════════════════════════════════════════════════════════════════════

# Каждая capability = действие, которое может выполнить роль.
CAPABILITIES: Dict[str, Set[str]] = {
    # ─── Заявки и доставки ───
    "delivery.create": {"coordinator", "foreman", "logistician", "curator"},
    "delivery.view_all": {"logistician", "curator"},
    "delivery.view_own": {"coordinator", "foreman", "driver"},
    "delivery.confirm_receipt": {"foreman", "logistician", "curator"},

    # ─── Маршруты ───
    "route.create": {"logistician", "curator"},
    "route.assign_driver": {"logistician", "curator"},
    "route.view_all": {"logistician", "curator"},
    "route.view_own": {"driver"},
    "route.add_pending_point": {"logistician", "curator"},
    "route.edit_pending_point": {"logistician", "curator"},
    "route.cancel_arrived_point": {"curator"},
    "route.cancel_full": {"logistician", "curator"},
    "route.start": {"driver"},
    "route.complete": {"driver"},
    "route.mark_delivered": {"driver"},

    # ─── Партии (Pipeline) ───
    "batch.create": {"production_chief", "curator"},
    "batch.change_status": {"production_chief", "curator"},
    "batch.view_facility": {"production_chief", "senior_worker", "worker", "supplier", "engineer"},
    "batch.view_target": {"coordinator", "foreman", "curator"},

    # ─── Смены ───
    "shift.start": {"installer", "worker", "driver", "foreman", "senior_worker"},
    "shift.end_own": {"installer", "worker", "driver", "foreman", "senior_worker"},
    "shift.pause_own": {"installer", "worker", "driver", "foreman", "senior_worker"},
    "shift.idle_own": {"installer", "worker", "driver", "foreman", "senior_worker"},
    "shift.audit_edit": {"curator"},
    "shift.reopen": {"curator"},
    "shift.view_team": {"foreman", "senior_worker", "production_chief", "curator", "coordinator"},

    # ─── Фото ───
    "photo.upload": {"installer", "worker", "driver", "foreman", "senior_worker"},
    "photo.approve": {"foreman", "coordinator", "curator", "production_chief"},
    "photo.reject": {"foreman", "coordinator", "curator", "production_chief"},
    "photo.view_facility_feed": {"production_chief", "senior_worker", "curator"},
    "photo.view_object_feed": {"coordinator", "foreman", "curator"},
    "photo.mark_problem": {"production_chief", "curator", "coordinator", "foreman"},

    # ─── Задачи ───
    "task.create": {"foreman", "coordinator", "curator", "production_chief", "senior_worker", "engineer"},
    "task.assign_to_installer": {"foreman", "coordinator", "curator"},
    "task.assign_to_worker": {"senior_worker", "production_chief", "engineer", "curator"},
    "task.close_own": {"installer", "worker", "foreman", "senior_worker", "engineer"},

    # ─── Объект ───
    "object.create": {"coordinator", "curator"},
    "object.edit": {"coordinator", "curator"},
    "object.view_history": {"coordinator", "foreman", "curator", "production_chief"},
    "object.export_pdf": {"coordinator", "curator"},

    # ─── Инструмент / материалы ───
    "tool.issue": {"foreman", "supplier", "curator"},
    "tool.return": {"foreman", "supplier", "installer", "worker", "curator"},
    "material.request": {"foreman", "coordinator", "senior_worker", "worker"},
    "material.issue": {"supplier"},
    "material.view_inventory": {"supplier", "production_chief", "curator"},

    # ─── Роли / админ ───
    "role.grant_other": {"curator"},  # выдать роль другому юзеру
    "role.revoke_other": {"curator"},
    "role.set_active_own": {"installer", "foreman", "coordinator", "curator", "driver", "logistician",
                            "production_chief", "senior_worker", "worker", "supplier", "engineer"},

    # ─── AI ───
    "ai.curator_dashboard": {"curator", "coordinator"},
    "ai.daily_summary": {"curator", "coordinator", "production_chief"},
    "ai.photo_search": {"curator"},
    "ai.materials_forecast": {"supplier", "production_chief", "curator"},
    "ai.idle_verify": {"curator", "foreman", "production_chief", "senior_worker"},
    "ai.smart_reply": {"installer", "foreman", "coordinator", "curator", "worker", "senior_worker"},
    "ai.support_triage": {"curator"},
    "ai.voice_input": {"installer", "worker", "foreman", "senior_worker"},
}


def role_capabilities(role: str) -> List[str]:
    """Вернёт список всех capabilities для роли."""
    role = (role or "").strip().lower()
    return sorted([cap for cap, roles in CAPABILITIES.items() if role in roles])


def has_capability(role: str, capability: str) -> bool:
    role = (role or "").strip().lower()
    return role in CAPABILITIES.get(capability, set())


def require_capability(user: User, capability: str) -> None:
    """FastAPI-стиль гвард. Использовать в endpoint: `require_capability(current_user, 'route.create')`."""
    if not has_capability(user.role or "", capability):
        raise HTTPException(
            status_code=403,
            detail=f"Capability '{capability}' не разрешена для роли '{user.role}'"
        )


# ═══════════════════════════════════════════════════════════════════════════
# Endpoint
# ═══════════════════════════════════════════════════════════════════════════

caps_router = APIRouter(prefix="/capabilities", tags=["capabilities"])


class CapabilityListOut(BaseModel):
    role: str
    capabilities: List[str]
    total: int


class CapabilityMatrixOut(BaseModel):
    """Полная матрица — для curator UI «выдать роль» с превью прав."""
    matrix: Dict[str, List[str]]  # cap_code → [role, role, ...]
    all_roles: List[str]
    all_capabilities: List[str]


@caps_router.get("/me", response_model=CapabilityListOut)
def my_capabilities(
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
):
    """Capabilities текущего юзера для активной роли. Также учитываем мульти-роль:
    объединяем capabilities всех активных ролей юзера."""
    rows = db.execute(text("""
        SELECT DISTINCT role FROM user_role_assignments
        WHERE user_id = :uid AND is_active = TRUE
    """), {"uid": current_user.id}).all()
    roles = [r[0] for r in rows] if rows else [current_user.role or ""]

    union_caps: Set[str] = set()
    for r in roles:
        union_caps.update(role_capabilities(r))

    return CapabilityListOut(
        role=",".join(roles),
        capabilities=sorted(union_caps),
        total=len(union_caps),
    )


@caps_router.get("/matrix", response_model=CapabilityMatrixOut)
def capability_matrix(
    current_user: User = Depends(get_current_user),
):
    """Полная матрица для curator-UI и audit'а. Доступно всем — никаких секретов в кодах."""
    return CapabilityMatrixOut(
        matrix={cap: sorted(roles) for cap, roles in CAPABILITIES.items()},
        all_roles=sorted({r for roles in CAPABILITIES.values() for r in roles}),
        all_capabilities=sorted(CAPABILITIES.keys()),
    )


@caps_router.get("/by-role/{role}", response_model=CapabilityListOut)
def capabilities_by_role(
    role: str,
    current_user: User = Depends(get_current_user),
):
    """Что может делать конкретная роль (для UI выдачи прав куратором)."""
    caps = role_capabilities(role)
    return CapabilityListOut(role=role, capabilities=caps, total=len(caps))
