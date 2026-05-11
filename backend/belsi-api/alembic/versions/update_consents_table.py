"""update_consents — таблица аудита согласий перед major-обновлением

Revision ID: g7h8i9j0k1l2
Revises: f6g7h8i9j0k1
Create Date: 2026-05-11

Назначение:
- Юридический след для 152-ФЗ при обновлении 1.2.5 → 2.0.0.
- При тапе «Скачать» в Update Gate Compose-диалоге фронт шлёт POST
  /audit/update-consent с {user_id, from, to, agreed_at, items}.
- Каждый чекбокс — отдельная запись поля `items` (JSONB массив).
- Запись хранится бессрочно (без TTL).

Backward compat: новая таблица, никаких изменений в существующих.
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


# revision identifiers, used by Alembic.
revision = "g7h8i9j0k1l2"
down_revision = "f6g7h8i9j0k1"
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.create_table(
        "update_consents",
        sa.Column("id", postgresql.UUID(as_uuid=True), primary_key=True,
                  server_default=sa.text("gen_random_uuid()")),
        sa.Column("user_id", postgresql.UUID(as_uuid=True),
                  sa.ForeignKey("users.id", ondelete="CASCADE"),
                  nullable=False, index=True),
        sa.Column("from_version", sa.String(length=32), nullable=False),
        sa.Column("to_version", sa.String(length=32), nullable=False),
        sa.Column("agreed_at", sa.DateTime(timezone=True),
                  server_default=sa.text("CURRENT_TIMESTAMP"),
                  nullable=False),
        # JSONB массив объектов {id, text} — копия 6 чекбоксов из gate-экрана
        sa.Column("items", postgresql.JSONB(astext_type=sa.Text()), nullable=False),
        # Контекст устройства / клиента для аудита
        sa.Column("device_info", sa.String(length=256), nullable=True),
        sa.Column("app_build", sa.Integer(), nullable=True),
        sa.Column("ip_address", sa.String(length=64), nullable=True),
        # Метаданные
        sa.Column("created_at", sa.DateTime(timezone=True),
                  server_default=sa.text("CURRENT_TIMESTAMP"),
                  nullable=False),
    )

    # Композитный индекс на (user_id, to_version) — для запросов
    # «согласился ли этот юзер на 2.0.0»
    op.create_index(
        "ix_update_consents_user_target",
        "update_consents",
        ["user_id", "to_version"],
    )

    # Индекс по дате — для аудитных отчётов
    op.create_index(
        "ix_update_consents_agreed_at",
        "update_consents",
        ["agreed_at"],
    )


def downgrade() -> None:
    op.drop_index("ix_update_consents_agreed_at", table_name="update_consents")
    op.drop_index("ix_update_consents_user_target", table_name="update_consents")
    op.drop_table("update_consents")
