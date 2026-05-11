#!/usr/bin/env python3
"""
Smoke-тест XeroCode AI Gateway для BELSI service-account.

Запуск:
    export XEROCODE_SERVICE_TOKEN=belsi_xxxxx_yyyyy
    python3 backend/belsi-api/scripts/smoke_test_xerocode.py

Проверяет:
- /health (no auth) → 200
- /usage (auth) → 200, видим квоты
- /templates (auth) → 8 шаблонов с fallback_chain
- /analyze-image с тест-фото → реальный JSON от Gemini
- /generate с daily_summary → реальный JSON
- /transcribe с тест-аудио → транскрипция

Запускается ЛОКАЛЬНО, не на проде. Прод BELSI api.belsi.ru не трогает.
"""
from __future__ import annotations

import asyncio
import base64
import os
import sys
import time
from pathlib import Path

import httpx

# Конфиг через env или дефолты
API_URL = os.environ.get("XEROCODE_API_URL", "https://xerocode.ru/api/v1/external")
TOKEN = os.environ.get("XEROCODE_SERVICE_TOKEN", "")

if not TOKEN:
    print("❌ XEROCODE_SERVICE_TOKEN не задан в env")
    print("   export XEROCODE_SERVICE_TOKEN=belsi_xxxxx_yyyyy")
    sys.exit(1)


# Простой 320x240 JPEG для тест-фото (1px белый, упакованный)
TEST_JPEG_B64 = (
    "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAAEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEB"
    "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/2wBDAQEBAQEBAQEBAQEBAQE"
    "BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQH/wA"
    "ARCAAYACADAREAAhEBAxEB/8QAFAABAAAAAAAAAAAAAAAAAAAAB//EABQQAQAAAAAAAAAAA"
    "AAAAAAAAAD/xAAUAQEAAAAAAAAAAAAAAAAAAAAH/8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/a"
    "AAwDAQACEQMRAD8AmQAA//Z"
)


async def test_health():
    print("\n[1/6] /health (no auth)...")
    async with httpx.AsyncClient(timeout=10.0) as client:
        r = await client.get(f"{API_URL}/health")
    if r.status_code == 200:
        d = r.json()
        print(f"  ✅ ok={d.get('ok')}, templates={d.get('templates')}")
    else:
        print(f"  ❌ HTTP {r.status_code}: {r.text[:200]}")


async def test_usage():
    print("\n[2/6] /usage...")
    async with httpx.AsyncClient(timeout=10.0) as client:
        r = await client.get(
            f"{API_URL}/usage",
            headers={"Authorization": f"Bearer {TOKEN}"},
        )
    if r.status_code == 200:
        d = r.json()
        print(f"  ✅ requests_today={d.get('requests_today')}, "
              f"budget=${d.get('cost_this_month_usd')}/{d.get('monthly_budget_usd')}")
    else:
        print(f"  ❌ HTTP {r.status_code}: {r.text[:200]}")


async def test_templates():
    print("\n[3/6] /templates...")
    async with httpx.AsyncClient(timeout=10.0) as client:
        r = await client.get(
            f"{API_URL}/templates",
            headers={"Authorization": f"Bearer {TOKEN}"},
        )
    if r.status_code == 200:
        d = r.json()
        templates = d.get("templates", [])
        if isinstance(templates, list):
            keys = [t.get("key") if isinstance(t, dict) else t for t in templates]
        else:
            keys = list(templates.keys()) if isinstance(templates, dict) else []
        print(f"  ✅ {len(keys)} шаблонов: {', '.join(keys)}")
    else:
        print(f"  ❌ HTTP {r.status_code}: {r.text[:200]}")


async def test_analyze_image():
    print("\n[4/6] /analyze-image (photo_quality)...")
    payload = {
        "image_base64": TEST_JPEG_B64,
        "prompt_template": "photo_quality",
        "request_id": f"smoke-photo-{int(time.time())}",
    }
    async with httpx.AsyncClient(timeout=60.0) as client:
        r = await client.post(
            f"{API_URL}/analyze-image",
            headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"},
            json=payload,
        )
    if r.status_code == 200:
        d = r.json()
        if d.get("ok"):
            result = d.get("result", {})
            meta = d.get("meta", {})
            print(f"  ✅ provider={meta.get('provider_used')}, model={meta.get('model_used')}")
            print(f"  ✅ score={result.get('score')}, comment={(result.get('comment') or '')[:80]}")
            print(f"  ✅ cost=${meta.get('cost_usd', 0):.6f}, duration={meta.get('duration_ms')}ms")
        else:
            print(f"  ❌ ok=false: {d.get('error')}")
    else:
        print(f"  ❌ HTTP {r.status_code}: {r.text[:300]}")


async def test_generate():
    print("\n[5/6] /generate (daily_summary)...")
    payload = {
        "prompt_template": "daily_summary",
        "data": {
            "date": "2026-05-10",
            "active_shifts": 14,
            "finished_shifts": 8,
            "work_hours": 87.5,
            "idle_hours": 4.2,
            "pause_hours": 11.3,
            "long_pauses": 2,
            "silent_objects": ["Углич-2"],
            "idle_reasons": [
                {"reason": "нет розетки", "count": 2},
                {"reason": "нет материала", "count": 1},
            ],
        },
        "request_id": f"smoke-summary-{int(time.time())}",
    }
    async with httpx.AsyncClient(timeout=60.0) as client:
        r = await client.post(
            f"{API_URL}/generate",
            headers={"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"},
            json=payload,
        )
    if r.status_code == 200:
        d = r.json()
        if d.get("ok"):
            result = d.get("result", {})
            meta = d.get("meta", {})
            print(f"  ✅ provider={meta.get('provider_used')}, model={meta.get('model_used')}")
            print(f"  ✅ headline: {result.get('headline')}")
            print(f"  ✅ summary: {(result.get('summary') or '')[:120]}...")
            print(f"  ✅ {len(result.get('anomalies') or [])} аномалий, "
                  f"{len(result.get('recommendations') or [])} рекомендаций")
        else:
            print(f"  ❌ ok=false: {d.get('error')}")
    else:
        print(f"  ❌ HTTP {r.status_code}: {r.text[:300]}")


async def test_transcribe():
    """
    FIX(2026-05-11) BELSI 2.0.0 (M4): smoke-тест транскрипции с реальным
    WAV-файлом тишины (16kHz, 1 сек). Whisper должен вернуть пустой или
    почти-пустой текст без падения.

    Чтобы протестировать с настоящей речью — положи MP3 в
    backend/belsi-api/scripts/fixtures/voice_sample.mp3 и запусти:
        TRANSCRIBE_FIXTURE=fixtures/voice_sample.mp3 python3 smoke_test_xerocode.py
    """
    print("\n[6/6] /transcribe (1s WAV silence)...")

    fixture_path = os.environ.get("TRANSCRIBE_FIXTURE")
    if fixture_path:
        p = Path(__file__).parent / fixture_path
        if not p.exists():
            print(f"  ⚠ fixture {p} не найден, пропускаю")
            return
        audio_bytes = p.read_bytes()
        mime = "audio/mpeg" if p.suffix == ".mp3" else "audio/wav"
        filename = p.name
    else:
        # Генерируем WAV 16kHz mono 1 секунду тишины (32 байта заголовка + 16000*2 байт данных)
        sample_rate = 16000
        n_samples = sample_rate  # 1 сек
        data_size = n_samples * 2  # 16-bit mono
        chunk_size = 36 + data_size
        header = (
            b"RIFF" + chunk_size.to_bytes(4, "little") + b"WAVE"
            + b"fmt " + (16).to_bytes(4, "little")
            + (1).to_bytes(2, "little")   # PCM
            + (1).to_bytes(2, "little")   # mono
            + sample_rate.to_bytes(4, "little")
            + (sample_rate * 2).to_bytes(4, "little")
            + (2).to_bytes(2, "little")   # block align
            + (16).to_bytes(2, "little")  # bits per sample
            + b"data" + data_size.to_bytes(4, "little")
        )
        audio_bytes = header + b"\x00" * data_size
        mime = "audio/wav"
        filename = "silence.wav"

    files = {"audio": (filename, audio_bytes, mime)}
    data = {
        "language": "ru",
        "prompt_hint": "BELSI smoke-test транскрипции",
        "request_id": f"smoke-transcribe-{int(time.time())}",
        "allow_paid_fallback": "false",
    }
    async with httpx.AsyncClient(timeout=120.0) as client:
        r = await client.post(
            f"{API_URL}/transcribe",
            headers={"Authorization": f"Bearer {TOKEN}"},
            files=files,
            data=data,
        )
    if r.status_code == 200:
        d = r.json()
        if d.get("ok"):
            result = d.get("result", {})
            meta = d.get("meta", {})
            print(f"  ✅ provider={meta.get('provider_used')}, model={meta.get('model_used')}")
            print(f"  ✅ text: '{(result.get('text') or '').strip()[:120]}'")
            print(f"  ✅ duration_audio={result.get('duration_seconds')}s, "
                  f"language={result.get('language_detected')}")
            print(f"  ✅ cost=${meta.get('cost_usd', 0):.6f}, "
                  f"latency={meta.get('duration_ms')}ms")
        else:
            print(f"  ❌ ok=false: {d.get('error')}")
    else:
        print(f"  ❌ HTTP {r.status_code}: {r.text[:300]}")


async def main():
    print(f"=== Smoke test XeroCode AI Gateway ===")
    print(f"URL:   {API_URL}")
    print(f"Token: {TOKEN[:12]}...{TOKEN[-4:]}")

    await test_health()
    await test_usage()
    await test_templates()
    await test_analyze_image()
    await test_generate()
    await test_transcribe()

    print("\n=== Smoke test complete ===")


if __name__ == "__main__":
    asyncio.run(main())
