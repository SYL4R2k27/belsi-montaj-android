# AI Integration · BELSI 1.3.0+

> Архитектурный документ интеграции AI-функций в BELSI.

## Принцип: Non-critical Optional AI Layer

Интеграция AI спроектирована как **non-critical optional layer**.
Ни один основной сценарий BELSI не зависит от успешности AI-вызова.
Все AI-результаты кэшируются и хранятся в БД BELSI. При недоступности
AI-провайдера система деградирует gracefully: пользовательские действия
продолжают работать, AI-поля остаются пустыми или получают статус
failed/deferred и/или заменяются автоматическим анализом (AI ver.1).

Это — **главный архитектурный инвариант**. Любое изменение в AI-слое
должно его соблюдать. Ревьюверу: если PR нарушает этот инвариант — отклонять.

## Платформа: XeroCode AI Office

[XeroCode](https://xerocode.ru) — российский AI orchestration layer.
BELSI использует его как единую точку входа во все AI-функции.

**Зачем через XeroCode, а не напрямую к моделям:**

1. **Унифицированный JSON-формат** — XeroCode абстрагирует разные
   AI-API в один envelope.
2. **Cost tracking + rate limits** — централизованно по сервис-аккаунтам.
3. **Идемпотентность** через `request_id` — XeroCode хранит
   response_cache и не повторяет уже выполненные вызовы.
4. **Fallback chain** — внутренний AI-провайдер скрывает выбор
   конкретной модели от BELSI. Если основная модель недоступна —
   XeroCode сам переключается на резервную.

## Архитектура потоков

```
┌─────────────┐                  ┌──────────────────┐
│ Android App │                  │ XeroCode AI      │
└──────┬──────┘                  │ Office           │
       │ HTTP                    │ xerocode.ru      │
       ▼                         └────────┬─────────┘
┌──────────────┐  HTTPS Bearer            │
│ BELSI Backend│  service-token           ▼
│ api.belsi.ru ├─────────────────► AI-обработка
│              │ ◄──────────────── result+meta
└──────┬───────┘
       │ INSERT
       ▼
┌──────────────┐
│ ai_analyses  │  ← все ответы сохраняются с request_id
│ shift_photos │  ← краткое в ai_comment / ai_score
└──────────────┘
```

## Типы AI-вызовов

| Use case | Endpoint BELSI | Sync/Async |
|---|---|---|
| photo_quality | (background при upload) | async |
| daily_summary | POST /curator/ai-daily-summary | sync, кэш 1ч |
| idle_verify | POST /shift/ai-verify-idle/{pause_id} | sync |
| voice_input | POST /shift/voice/transcribe | sync, multipart |
| triage_ticket | POST /support/ai-triage/{ticket_id} | sync |
| smart_reply | POST /messenger/ai-suggest-replies | sync |
| photo_search | POST /curator/ai-photo-search | sync |
| stock_forecast | GET /production/materials/ai-forecast | sync, кэш 6ч |

## Хранилище: таблица `ai_analyses`

Универсальное хранилище ВСЕХ AI-ответов с привязкой к сущностям
(фото / смена / простой / партия / юзер). Идемпотентность через
уникальный `request_id`. Корректировка человеком — через
`corrected_by/corrected_json/correction_note`.

Подробная схема — миграция `f6g7h8i9j0k1` в `alembic/versions/`.

## Деградация при сбое AI

Каждый вызов AI на стороне BELSI обёрнут в try/except и **не бросает**
исключения наружу — возвращает None.

Стратегия деградации по endpoint:
- **photo_quality** → fallback на локальный Pillow-анализ
- **daily_summary** → возврат raw stats без AI-комментария
- **idle_verify** → пометка «AI временно недоступен», куратор делает вручную
- **voice_transcribe** → пользователь набирает текст руками
- **triage_ticket** → priority=normal по умолчанию (graceful fallback)
- **smart_reply** → 3 шаблонных ответа («Понял», «Уточню», «Подробнее»)
- **photo_search** → SQL-фильтр без AI-парсинга, либо кэшированный результат
- **stock_forecast** → собственный простой расчёт по min_stock

## Feature flag: `XEROCODE_ENABLED`

Переменная окружения BELSI backend.

- `XEROCODE_ENABLED=true` (default) — AI-вызовы идут через XeroCode
- `XEROCODE_ENABLED=false` — все AI-вызовы становятся no-op (return None),
  система откатывается на до-AI-поведение

При обнаружении сбоев — переключение на `false`, рестарт `belsi-api`,
система продолжает работать. Не нужен полный rollback.

## Защита бюджета: `allow_paid_fallback`

По умолчанию **`False`** для всех вызовов из BELSI — используются
только бесплатные модели. `allow_paid_fallback=True` можно установить
per-call для критичных запросов.

## Branding в продукте

- **Android «О приложении»**: «AI-функции реализованы на платформе
  XeroCode AI Office. xerocode.ru»
- **Куратор-админка footer**: «AI processing powered by XeroCode»
- **Этот документ**: технический reference

## Мониторинг

После деплоя следить за:

- **`ai_analyses` rows/day** — нормальная нагрузка ~600+ в день
- **`provider_used`** распределение
- **`paid_fallback_used = TRUE`** — должно быть = 0 без явного `allow_paid_fallback=true`
- **`cost_usd` SUM month** — следить, чтобы не превысил бюджет

Если что-то идёт не так — `XEROCODE_ENABLED=false` + рестарт,
до-AI-поведение восстанавливается мгновенно.
