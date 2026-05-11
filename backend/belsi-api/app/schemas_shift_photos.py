from datetime import datetime
from uuid import UUID
from pydantic import BaseModel, ConfigDict, field_validator

class ShiftPhotoOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    shift_id: UUID
    hour_label: str | None = None
    status: str
    comment: str | None = None
    photo_url: str
    created_at: datetime
    ai_comment: str | None = None
    ai_score: int | None = None
    ai_category: str | None = 'unknown'

    @field_validator('ai_category', mode='before')
    @classmethod
    def _normalize_ai_category(cls, v):
        return v if v else 'unknown'

    @field_validator('status', mode='before')
    @classmethod
    def _normalize_status(cls, v):
        return v if v else 'pending'
