from pydantic import BaseModel
from typing import Optional


class YandexAuthStartResponse(BaseModel):
    auth_url: str


class YandexAuthResult(BaseModel):
    status: str
    token: str
    phone: str
    provider: str = "yandex"
    provider_user_id: Optional[str] = None
