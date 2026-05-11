
# app/settings.py

import os
from dataclasses import dataclass
from functools import lru_cache


@dataclass
class Settings:

    # PostgreSQL
    db_host: str = os.getenv("DB_HOST", "localhost")
    db_port: int = int(os.getenv("DB_PORT", "5432"))
    db_name: str = os.getenv("DB_NAME", "default_db")
    db_user: str = os.getenv("DB_USER", "gen_user")
    db_password: str = os.getenv("DB_PASSWORD", "")

    # Redis
    redis_host: str = os.getenv("REDIS_HOST", "localhost")
    redis_port: int = int(os.getenv("REDIS_PORT", "6379"))
    redis_username: str = os.getenv("REDIS_USERNAME", "default")
    redis_password: str = os.getenv("REDIS_PASSWORD", "")

    # S3 (если понадобится через settings, сейчас storage.py читает из os.environ)
    s3_endpoint: str = os.getenv("S3_ENDPOINT", "")
    s3_bucket: str = os.getenv("S3_BUCKET", "")
    s3_access_key: str = os.getenv("S3_ACCESS_KEY", "")
    s3_secret_key: str = os.getenv("S3_SECRET_KEY", "")
    s3_public_base: str = os.getenv("S3_PUBLIC_BASE", "")

    # Yandex OAuth
    yandex_client_id: str = os.getenv("YANDEX_CLIENT_ID", "")
    yandex_client_secret: str = os.getenv("YANDEX_CLIENT_SECRET", "")
    yandex_redirect_uri: str = os.getenv("YANDEX_REDIRECT_URI", "https://api.belsi.ru/auth/yandex/callback")
    yandex_auth_url: str = os.getenv("YANDEX_AUTH_URL", "https://oauth.yandex.ru/authorize")
    yandex_token_url: str = os.getenv("YANDEX_TOKEN_URL", "https://oauth.yandex.ru/token")
    yandex_userinfo_url: str = os.getenv("YANDEX_USERINFO_URL", "https://login.yandex.ru/info?format=json")
    # FIX(2026-05-04): расширили скоупы — нужны телефон, ДР и аватар для обогащения профиля.
    # В кабинете oauth.yandex.ru все права уже включены (см. скриншот пользователя).
    yandex_scopes: str = os.getenv(
        "YANDEX_SCOPES",
        "login:info login:email login:default_phone login:birthday login:avatar",
    )
    yandex_app_deeplink: str = os.getenv("YANDEX_APP_DEEPLINK", "belsiwork://auth/yandex")
    
    # Sber ID (OAuth/OIDC)
    sber_client_id: str = os.getenv("SBER_CLIENT_ID", "")
    sber_client_secret: str = os.getenv("SBER_CLIENT_SECRET", "")
    sber_redirect_uri: str = os.getenv("SBER_REDIRECT_URI", "https://api.belsi.ru/auth/sber/callback")

    # URL эндпоинтов Сбер (заполните реальными значениями из кабинета/документации)
    sber_auth_url: str = os.getenv("SBER_AUTH_URL", "")
    sber_token_url: str = os.getenv("SBER_TOKEN_URL", "")
    sber_userinfo_url: str = os.getenv("SBER_USERINFO_URL", "")

    # scopes (что запросим у Сбера)
    sber_scopes: str = os.getenv("SBER_SCOPES", "openid profile phone")

    # Deep link обратно в приложение (опционально)
    sber_app_deeplink: str = os.getenv("SBER_APP_DEEPLINK", "belsiwork://auth/sber")

    # JWT
    jwt_secret: str = os.getenv("JWT_SECRET", "")
    jwt_algorithm: str = "HS256"
    jwt_expire_hours: int = int(os.getenv("JWT_EXPIRE_HOURS", "720"))  # 30 дней

    @property
    def database_url(self) -> str:
        # строка подключения для Alembic (sync-движок psycopg2)
        return (
            f"postgresql+psycopg2://{self.db_user}:"
            f"{self.db_password}@{self.db_host}:{self.db_port}/{self.db_name}"
        )

    # API base URL (для ссылок, если нужно)
    api_base_url: str = os.getenv("API_BASE_URL", "https://api.belsi.ru")


@lru_cache
def get_settings() -> Settings:
    return Settings()


# чтобы старый код `from .settings import settings` продолжал работать
settings = get_settings()
