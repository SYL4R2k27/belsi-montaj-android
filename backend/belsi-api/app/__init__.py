from fastapi import FastAPI
from pydantic import BaseModel

app = FastAPI(title="BELSI.Work API")


# ----- МОДЕЛИ ЗАПРОСОВ -----

class PhoneRequest(BaseModel):
    phone: str


class OTPVerifyRequest(BaseModel):
    phone: str
    code: str


# ----- ХЕЛСЧЕК -----

@app.get("/health")
def health():
    return {"status": "ok"}


# ----- АВТОРИЗАЦИЯ ПО ТЕЛЕФОНУ -----

@app.post("/auth/phone")
def auth_phone(payload: PhoneRequest):
    """
    Заглушка: принимаем номер телефона, как будто отправили SMS-код.
    Реальная отправка будет позже.
    """
    # TODO: здесь можно будет сохранить phone в БД, выдать request_id и отправить SMS
    return {"status": "ok"}


@app.post("/auth/verify")
def auth_verify(payload: OTPVerifyRequest):
    """
    Заглушка: считаем код '0000' корректным.
    Всё остальное — ошибка.
    """
    if payload.code == "0000":
        # TODO: здесь позже будет генерация реального токена
        return {
            "status": "ok",
            "token": "dev-token",
            "role": "installer",
        }
    else:
        return {
            "status": "error",
            "reason": "invalid_code"
        }
