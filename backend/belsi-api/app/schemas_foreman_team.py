# app/schemas_foreman_team.py
from __future__ import annotations

from datetime import datetime
from uuid import UUID
from typing import List, Optional

from pydantic import BaseModel, ConfigDict


class ForemanTeamMemberOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: Optional[str] = None
    phone: str
    user_id: Optional[str] = None
    role: Optional[str] = None
    full_name: Optional[str] = None
    first_name: Optional[str] = None
    last_name: Optional[str] = None

    # Статус работы
    last_shift_at: Optional[datetime] = None
    active_shift_id: Optional[str] = None
    is_working_now: bool = False

    # Фото
    last_photo_at: Optional[datetime] = None
    pending_photos_count: int = 0

    # Статистика
    total_shifts: int = 0
    total_hours: float = 0.0

    # Когда присоединился к команде
    joined_at: Optional[datetime] = None


class ForemanTeamOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    items: List[ForemanTeamMemberOut]
    count: int


class ForemanTeamMemberRemoveRequest(BaseModel):
    installer_id: UUID


# Legacy aliases for backward compatibility
ForemanInstallerItem = ForemanTeamMemberOut
ForemanInstallerListOut = ForemanTeamOut
ForemanInstallerRemoveRequest = ForemanTeamMemberRemoveRequest
