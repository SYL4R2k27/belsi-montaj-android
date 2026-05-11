from pydantic import BaseModel
from typing import Optional

class SberInitResponse(BaseModel):
    auth_url: str
    state: str
    nonce: str
    code_challenge: str
    code_challenge_method: str = "S256"

class SberExchangeRequest(BaseModel):
    code: str
    state: str

class SberAuthResponse(BaseModel):
    status: str = "ok"
    token: str
    phone: Optional[str] = None
