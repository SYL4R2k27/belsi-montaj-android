"""Yandex profile enrichment: birthday, avatar_url, oauth_provider, oauth_subject

Revision ID: c3d4e5f6g7h8
Revises: b2c3d4e5f6g7
Create Date: 2026-05-04

Назначение:
- Добавить колонки для обогащённых данных из Yandex OAuth: дата рождения, URL аватара,
  провайдер OAuth и стабильный ID от провайдера (oauth_subject).
- Backfill существующих yandex-юзеров (phone='yandex:<id>') в новые колонки
  oauth_provider='yandex', oauth_subject='<id>'.

Backward compat: все колонки nullable, без default-значений; существующие 1.2.5
клиенты не затрагиваются.
"""
from alembic import op
import sqlalchemy as sa

revision = "c3d4e5f6g7h8"
down_revision = "b2c3d4e5f6g7"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 1. Новые колонки в users
    op.add_column("users", sa.Column("birthday", sa.Date(), nullable=True))
    op.add_column("users", sa.Column("avatar_url", sa.Text(), nullable=True))
    op.add_column("users", sa.Column("oauth_provider", sa.Text(), nullable=True))
    op.add_column("users", sa.Column("oauth_subject", sa.Text(), nullable=True))

    # 2. Уникальный partial-индекс: один и тот же oauth_subject от одного провайдера
    #    не может принадлежать двум разным юзерам. NULL не учитываются.
    op.execute(
        "CREATE UNIQUE INDEX IF NOT EXISTS uq_users_oauth "
        "ON users (oauth_provider, oauth_subject) "
        "WHERE oauth_subject IS NOT NULL"
    )

    # 3. Backfill: существующие yandex:<id> юзеры получают oauth_provider/subject.
    #    SUBSTRING(phone FROM 8) = всё после 'yandex:' (7 символов + 1).
    op.execute(
        "UPDATE users "
        "SET oauth_provider = 'yandex', "
        "    oauth_subject = SUBSTRING(phone FROM 8) "
        "WHERE phone LIKE 'yandex:%' AND oauth_subject IS NULL"
    )


def downgrade() -> None:
    op.execute("DROP INDEX IF EXISTS uq_users_oauth")
    op.drop_column("users", "oauth_subject")
    op.drop_column("users", "oauth_provider")
    op.drop_column("users", "avatar_url")
    op.drop_column("users", "birthday")
