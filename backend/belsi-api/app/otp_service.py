import re
import time
from dataclasses import dataclass
from fastapi import APIRouter

router = APIRouter()
import redis
from datetime import datetime, timedelta
from uuid import uuid4

from .settings import settings
from .sms import send_otp_via_smsru, SmsSendError

# ⬇️ ПОДКЛЮЧЕНИЕ К REDIS (ИСПОЛЬЗУЕМ ТВОИ ДАННЫЕ)
# ХОРОШО БЫ ВЫНЕСТИ В ENV-ПЕРЕМЕННЫЕ, НО ПОКА ЯВНО

REDIS_HOST = "192.168.56.4"
REDIS_PORT = 6379
REDIS_USER = "default"
REDIS_PASSWORD = ">,u_(3RK@unUTc"  # ▷ В ПРОДЕ лучше хранить в ENV

redis_client = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    username=REDIS_USER,
    password=REDIS_PASSWORD,
    decode_responses=True,  # строки, а не bytes
)


def normalize_phone(raw: str) -> str:
    """
    Приводим номер к единому формату.
    Сейчас: считаем, что это РФ и храним как +7XXXXXXXXXX.
    При необходимости допилим под другие страны.
    """
    digits = re.sub(r"\D", "", raw or "")

    if not digits:
        return ""

    if digits.startswith("8"):
        digits = "7" + digits[1:]

    if not digits.startswith("7"):
        # тут можно обрабатывать другие страны
        pass

    return "+" + digits


@dataclass
class OTPData:
    phone: str
    code: str
    expires_at: float  # unix timestamp
    attempts: int = 0


class OTPService:
    """
    Сервис для работы с одноразовыми кодами:
    - генерация
    - сохранение в Redis
    - проверка
    """

    KEY_TEMPLATE = "otp:{phone}"

    def __init__(self, client: redis.Redis):
        self.client = client

    def _key(self, phone: str) -> str:
        return self.KEY_TEMPLATE.format(phone=phone)

    def generate_code(self) -> str:
        import random
        return f"{random.randint(0, 999_999):06d}"

    def save_code(self, phone: str, code: str, ttl_seconds: int = 300) -> OTPData:
        """
        Сохраняем код в Redis с TTL.
        """
        now = time.time()
        expires_at = now + ttl_seconds

        key = self._key(phone)
        # Храним просто код. TTL в Redis уже сам отвечает за "expires".
        self.client.setex(key, ttl_seconds, code)

        return OTPData(phone=phone, code=code, expires_at=expires_at, attempts=0)

    def verify_code(self, phone: str, code: str, max_attempts: int = 5) -> bool:
        """
        Проверяем код:
        - есть ли он
        - совпадает ли
        - не превышен ли лимит попыток (этот пункт можно доработать)
        Сейчас делаем простую проверку: если код совпал — удаляем.
        """
        key = self._key(phone)
        stored = self.client.get(key)

        if stored is None:
            # нет кода или истёк TTL
            return False

        if code != stored:
            # можно дописать учёт попыток в отдельном ключе
            return False

        # всё ок — удаляем код
        self.client.delete(key)
        return True

# get_current_user — единая реализация в auth.py (JWT + обратная совместимость)
# Реэкспорт для обратной совместимости: `from .otp_service import get_current_user`
from .auth import get_current_user  # noqa: F401

otp_service = OTPService(redis_client)


# ======== SMS ========

def send_sms(phone: str, code: str) -> None:
    """
    Здесь интеграция с реальным SMS-провайдером.

    Вариант:
    - SMS.ru, Twilio, TurboSMS, любой локальный провайдер.
    - Обычно это HTTP-запрос с токеном/ключом.

    СЕЙЧАС: просто логируем в консоль (для отладки).
    Как только появятся реквизиты SMS-провайдера, сюда добавляем
    реальный HTTP-запрос.
    """
    print(f"[SMS] Sending OTP to {phone}: code={code}")
    # Пример наброска (ПРИМЕР, НЕ РАБОТАЮЩИЙ ИЗ КОРОБКИ):
    #
    # import requests
    # SMS_API_URL = "https://sms-provider.example.com/send"
    # SMS_API_KEY = "YOUR_API_KEY"
    # text = f"Код входа в BELSI.Work: {code}"
    # requests.post(SMS_API_URL, data={
    #     "api_key": SMS_API_KEY,
    #     "to": phone,
    #     "text": text,
    # })
