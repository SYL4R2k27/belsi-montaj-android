import asyncio
import os
import uuid
from functools import lru_cache

import boto3


# ========= Настройки S3 =========
S3_ENDPOINT = os.getenv("S3_ENDPOINT", "https://s3.twcstorage.ru")
S3_BUCKET = os.getenv("S3_BUCKET", "46a58074-ea583b63-7b06-4d9e-ac2b-4a519ee3477b")
S3_ACCESS_KEY = os.getenv("S3_ACCESS_KEY", "")
S3_SECRET_KEY = os.getenv("S3_SECRET_KEY", "")

S3_PUBLIC_BASE = os.getenv("S3_PUBLIC_BASE", "https://bucket.api.belsi.ru")

CONTENT_TYPE_MAP = {
    ".pdf": "application/pdf",
    ".doc": "application/msword",
    ".docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    ".xls": "application/vnd.ms-excel",
    ".xlsx": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    ".png": "image/png",
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".zip": "application/zip",
    ".rar": "application/x-rar-compressed",
    ".dwg": "application/acad",
    ".dxf": "application/dxf",
    ".mp3": "audio/mpeg",
    ".ogg": "audio/ogg",
    ".m4a": "audio/mp4",
}


@lru_cache
def _get_s3_client():
    if not S3_ACCESS_KEY or not S3_SECRET_KEY:
        raise RuntimeError("S3_ACCESS_KEY / S3_SECRET_KEY не заданы в окружении")

    return boto3.client(
        "s3",
        endpoint_url=S3_ENDPOINT,
        aws_access_key_id=S3_ACCESS_KEY,
        aws_secret_access_key=S3_SECRET_KEY,
    )


def _guess_content_type(filename: str | None) -> str:
    """Guess content type from filename extension."""
    if filename and "." in filename:
        ext = "." + filename.rsplit(".", 1)[-1].lower()
        return CONTENT_TYPE_MAP.get(ext, "application/octet-stream")
    return "application/octet-stream"


async def save_shift_photo(content: bytes, filename: str | None = None) -> str:
    client = _get_s3_client()

    ext = ".jpg"
    if filename and "." in filename:
        ext = "." + filename.rsplit(".", 1)[-1].lower()

    key = f"shift_photos/{uuid.uuid4()}{ext}"

    loop = asyncio.get_event_loop()
    await loop.run_in_executor(
        None,
        lambda: client.put_object(
            Bucket=S3_BUCKET,
            Key=key,
            Body=content,
            ContentType="image/jpeg",
            ACL="public-read",
        ),
    )

    url = f"{S3_PUBLIC_BASE}/{key}"
    return url


async def upload_file(content: bytes, filename: str | None = None, prefix: str = "tools") -> str:
    """
    Универсальная функция для загрузки файлов в S3.
    Принимает байты, сохраняет в S3 и возвращает публичный URL.
    """
    client = _get_s3_client()

    ext = ".bin"
    if filename and "." in filename:
        ext = "." + filename.rsplit(".", 1)[-1].lower()

    content_type = _guess_content_type(filename)
    key = f"{prefix}/{uuid.uuid4()}{ext}"

    loop = asyncio.get_event_loop()
    await loop.run_in_executor(
        None,
        lambda: client.put_object(
            Bucket=S3_BUCKET,
            Key=key,
            Body=content,
            ContentType=content_type,
        ),
    )

    return f"{S3_PUBLIC_BASE}/{key}"
