from pydantic import BaseModel
from uuid import UUID
from typing import Optional

class ForemanInviteCancelRequest(BaseModel):
    code: Optional[str] = None
    id: Optional[UUID] = None
