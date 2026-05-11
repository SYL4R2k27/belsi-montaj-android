import os
import httpx


SMSRU_API_ID = os.getenv("SMSRU_API_ID")
SMSRU_SENDER = os.getenv("SMSRU_SENDER")  # необязательно


class SmsSendError(Exception):
    pass


async def send_otp_via_smsru(phone: str, code: str) -> None:
    """
    Отправляем OTP-код через sms.ru
    """
    if not SMSRU_API_ID:
        # В проде лучше логировать, а не кидать наружу текст ошибки,
        # но сейчас нам важно явно видеть проблему.
        raise SmsSendError("SMSRU_API_ID не настроен на сервере")

    text = f"Код входа BELSI.Монтаж: {code}"

    payload = {
        "api_id": SMSRU_API_ID,
        "to": phone,
        "msg": text,
        "json": 1,
    }
    if SMSRU_SENDER:
        payload["from"] = SMSRU_SENDER

    async with httpx.AsyncClient(timeout=10) as client:
        resp = await client.post("https://sms.ru/sms/send", data=payload)

    data = resp.json()

    # Успешно, если status == "OK"
    if data.get("status") != "OK":
        status_code = data.get("status_code")
        status_text = data.get("status_text")
        raise SmsSendError(f"sms.ru error {status_code}: {status_text}")
