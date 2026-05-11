"""ai_analyses table — универсальное хранилище ответов AI

Revision ID: f6g7h8i9j0k1
Revises: e5f6g7h8i9j0
Create Date: 2026-05-10

Назначение:
- Хранить все AI-ответы от XeroCode-gateway (photo_quality, daily_summary,
  idle_verify, voice_transcribe, triage_ticket, chat_reply_suggest,
  query_to_filter, stock_forecast).
- Привязка к конкретной сущности (фото / смена / простой / партия / юзер).
- Аудит: что AI сказал, какая модель, сколько токенов.
- Идемпотентность: уникальный request_id предотвращает повторы.
- Корректировка человеком: corrected_by/corrected_json/correction_note.

Backward compat: всё аддитивно, существующие данные не трогаются.
shift_photos.ai_* остаются — там лежит краткая выжимка для быстрого
показа в списке. Полный JSON — в ai_analyses.

Безопасно для прод 1.2.5: новая таблица не используется существующим
кодом, не блокирует никакие операции. При rollback просто DROP.
"""
from alembic import op
import sqlalchemy as sa


revision = "f6g7h8i9j0k1"
down_revision = "e5f6g7h8i9j0"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.execute("""
        CREATE TABLE IF NOT EXISTS ai_analyses (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

            -- Один из FK (множественные nullable, чтобы привязать к разным сущностям)
            shift_photo_id UUID REFERENCES shift_photos(id) ON DELETE CASCADE,
            shift_id       UUID REFERENCES shifts(id) ON DELETE CASCADE,
            pause_id       UUID REFERENCES shift_pauses(id) ON DELETE CASCADE,
            batch_id       UUID REFERENCES production_batches(id) ON DELETE CASCADE,
            user_id        UUID REFERENCES users(id) ON DELETE SET NULL,

            -- Тип анализа: photo_quality / daily_summary / idle_verify / voice_transcribe /
            -- triage_ticket / chat_reply_suggest / query_to_filter / stock_forecast
            analysis_type TEXT NOT NULL,

            -- Полный JSON-ответ от XeroCode (поле result из ExternalEnvelope)
            result_json   JSONB NOT NULL,

            -- Человеко-читаемое (для быстрого показа без парсинга JSON)
            result_text   TEXT,

            -- Уверенность модели 0-100 (если возвращает)
            confidence    INTEGER,

            -- Метаданные XeroCode
            model_used    TEXT,
            provider_used TEXT,
            tokens_input  INTEGER,
            tokens_output INTEGER,
            cost_usd      NUMERIC(10,6),
            duration_ms   INTEGER,

            -- Идемпотентность: 'belsi-photo-{uuid}', 'belsi-summary-{date}',
            -- 'belsi-idle-{pause_id}', и т.д.
            request_id    TEXT NOT NULL,

            -- Использовался ли paid fallback (anthropic, openai, apiyi)?
            paid_fallback_used BOOLEAN NOT NULL DEFAULT FALSE,

            -- Аудит
            created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),

            -- Корректировка от куратора/координатора
            corrected_by    UUID REFERENCES users(id) ON DELETE SET NULL,
            corrected_at    TIMESTAMPTZ,
            corrected_json  JSONB,
            correction_note TEXT
        )
    """)

    # Уникальность по request_id предотвращает дублирующие записи
    # (на стороне XeroCode тоже есть idempotency через response_cache,
    # но дополнительная защита на нашей стороне — паранойя оправдана).
    op.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_ai_analyses_request_id ON ai_analyses(request_id)")

    # Индексы для частых запросов
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_analyses_photo ON ai_analyses(shift_photo_id) WHERE shift_photo_id IS NOT NULL")
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_analyses_pause ON ai_analyses(pause_id) WHERE pause_id IS NOT NULL")
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_analyses_batch ON ai_analyses(batch_id) WHERE batch_id IS NOT NULL")
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_analyses_user ON ai_analyses(user_id) WHERE user_id IS NOT NULL")
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_analyses_type_date ON ai_analyses(analysis_type, created_at DESC)")

    # Для дашборда расходов по моделям
    op.execute("CREATE INDEX IF NOT EXISTS idx_ai_analyses_provider_date ON ai_analyses(provider_used, created_at DESC) WHERE provider_used IS NOT NULL")


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS ai_analyses CASCADE")
