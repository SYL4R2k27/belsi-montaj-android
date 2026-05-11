"""add role_change_log table and lat/lng to site_objects

Revision ID: b2c3d4e5f6g7
Revises: a1b2c3d4e5f6
Create Date: 2026-03-16
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import UUID

# revision identifiers, used by Alembic.
revision = 'b2c3d4e5f6g7'
down_revision = 'a1b2c3d4e5f6'
branch_labels = None
depends_on = None


def upgrade() -> None:
    # 3.13 — Таблица аудита смены ролей
    op.create_table(
        'role_change_log',
        sa.Column('id', UUID(as_uuid=True), primary_key=True, server_default=sa.text('gen_random_uuid()')),
        sa.Column('user_id', UUID(as_uuid=True), sa.ForeignKey('users.id'), nullable=False, index=True),
        sa.Column('old_role', sa.String(32), nullable=False),
        sa.Column('new_role', sa.String(32), nullable=False),
        sa.Column('changed_by', UUID(as_uuid=True), sa.ForeignKey('users.id'), nullable=True),
        sa.Column('changed_at', sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
    )

    # 3.16 — Координаты для объектов (подготовка к карте)
    op.add_column('site_objects', sa.Column('latitude', sa.Float(), nullable=True))
    op.add_column('site_objects', sa.Column('longitude', sa.Float(), nullable=True))


def downgrade() -> None:
    op.drop_column('site_objects', 'longitude')
    op.drop_column('site_objects', 'latitude')
    op.drop_table('role_change_log')
