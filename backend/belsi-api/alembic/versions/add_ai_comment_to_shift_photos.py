"""add ai_comment and ai_analyzed_at to shift_photos

Revision ID: a1b2c3d4e5f6
Revises: 425f115b569b
Create Date: 2026-03-16
"""
from alembic import op
import sqlalchemy as sa

# revision identifiers, used by Alembic.
revision = 'a1b2c3d4e5f6'
down_revision = '425f115b569b'
branch_labels = None
depends_on = None


def upgrade() -> None:
    op.add_column('shift_photos', sa.Column('ai_comment', sa.Text(), nullable=True))
    op.add_column('shift_photos', sa.Column('ai_analyzed_at', sa.DateTime(timezone=True), nullable=True))


def downgrade() -> None:
    op.drop_column('shift_photos', 'ai_analyzed_at')
    op.drop_column('shift_photos', 'ai_comment')
