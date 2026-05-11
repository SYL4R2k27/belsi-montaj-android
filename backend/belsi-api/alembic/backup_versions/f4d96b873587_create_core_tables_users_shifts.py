"""create core tables (users, shifts, photos, invites)

Revision ID: abcd1234create
Revises: 7e2e88b3ebac
Create Date: 2025-11-24 12:40:00.000000
"""

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

# revision identifiers, used by Alembic.
revision = "f4d96b873587"
down_revision = "7e2e88b3ebac"  # последний твой ID, сейчас это 7e2e88b3ebac
branch_labels = None
depends_on = None


def upgrade():
    # =============== users ===============
    op.create_table(
        "users",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            nullable=False,
        ),
        sa.Column("phone", sa.String(length=32), nullable=False, unique=True),
        sa.Column("role", sa.String(length=32), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(),
            server_default=sa.text("now()"),
            nullable=False,
        ),
    )

    # =============== shifts ===============
    op.create_table(
        "shifts",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            nullable=False,
        ),
        sa.Column(
            "user_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "started_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.Column("finished_at", sa.DateTime(), nullable=True),
        sa.Column("status", sa.String(length=32), nullable=False, server_default="active"),
        sa.Column(
            "created_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("now()"),
        ),
    )
    op.create_index("idx_shifts_user_id", "shifts", ["user_id"])

    # =============== shift_photos ===============
    op.create_table(
        "shift_photos",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            nullable=False,
        ),
        sa.Column(
            "shift_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("shifts.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("hour_label", sa.String(length=32), nullable=False),
        sa.Column("photo_url", sa.String(length=512), nullable=False),
        sa.Column("status", sa.String(length=32), nullable=False, server_default="pending"),
        sa.Column("comment", sa.String(length=512), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("now()"),
        ),
    )
    op.create_index("idx_shift_photos_shift_id", "shift_photos", ["shift_id"])

    # =============== foreman_invites ===============
    op.create_table(
        "foreman_invites",
        sa.Column(
            "id",
            postgresql.UUID(as_uuid=True),
            primary_key=True,
            nullable=False,
        ),
        sa.Column(
            "foreman_id",
            postgresql.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("code", sa.String(length=16), nullable=False, unique=True),
        sa.Column("status", sa.String(length=16), nullable=False, server_default="new"),
        sa.Column("installer_phone", sa.String(length=32), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(),
            nullable=False,
            server_default=sa.text("now()"),
        ),
        sa.Column("expires_at", sa.DateTime(), nullable=True),
        sa.Column("used_at", sa.DateTime(), nullable=True),
    )


def downgrade():
    op.drop_table("foreman_invites")
    op.drop_index("idx_shift_photos_shift_id", table_name="shift_photos")
    op.drop_table("shift_photos")
    op.drop_index("idx_shifts_user_id", table_name="shifts")
    op.drop_table("shifts")
    op.drop_table("users")
