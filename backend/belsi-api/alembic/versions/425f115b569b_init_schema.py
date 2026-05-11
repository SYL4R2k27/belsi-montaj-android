"""init schema

Revision ID: 425f115b569b
Revises: 
Create Date: 2025-11-24 13:20:33.351759

"""
"""init schema

Revision ID: 425f115b569b
Revises:
Create Date: 2025-11-24 13:20:33.351759

"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql as pg


# revision identifiers, used by Alembic.
revision = "425f115b569b"
down_revision = None
branch_labels = None
depends_on = None


def upgrade():
    # на всякий случай включим uuid-генератор
    op.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto;")

    # ---------- users ----------
    op.create_table(
        "users",
        sa.Column(
            "id",
            pg.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("phone", sa.String(length=32), nullable=False, unique=True),
        sa.Column("role", sa.String(length=32), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )

    # ---------- shifts ----------
    op.create_table(
        "shifts",
        sa.Column(
            "id",
            pg.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "user_id",
            pg.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "started_at",
            sa.DateTime(timezone=True),
            nullable=False,
        ),
        sa.Column(
            "finished_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
        sa.Column(
            "status",
            sa.String(length=32),
            nullable=False,
            server_default=sa.text("'active'"),
        ),
        sa.Column(
            "hourly_rate",
            sa.Numeric(10, 2),
            nullable=True,
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )
    op.create_index(
        "idx_shifts_user_id",
        "shifts",
        ["user_id"],
    )

    # ---------- shift_photos ----------
    op.create_table(
        "shift_photos",
        sa.Column(
            "id",
            pg.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "shift_id",
            pg.UUID(as_uuid=True),
            sa.ForeignKey("shifts.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("hour_label", sa.String(length=32), nullable=True),
        sa.Column("photo_url", sa.String(length=512), nullable=False),
        sa.Column(
            "status",
            sa.String(length=32),
            nullable=False,
            server_default=sa.text("'pending'"),
        ),
        sa.Column("comment", sa.Text(), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )
    op.create_index(
        "idx_shift_photos_shift_id",
        "shift_photos",
        ["shift_id"],
    )

    # ---------- foreman_invites ----------
    op.create_table(
        "foreman_invites",
        sa.Column(
            "id",
            pg.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column("code", sa.String(length=16), nullable=False, unique=True),
        sa.Column("foreman_phone", sa.String(length=32), nullable=False),
        sa.Column("installer_phone", sa.String(length=32), nullable=True),
        sa.Column(
            "status",
            sa.String(length=32),
            nullable=False,
            server_default=sa.text("'new'"),
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "expires_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
        sa.Column(
            "used_at",
            sa.DateTime(timezone=True),
            nullable=True,
        ),
    )

    # ---------- support_tickets ----------
    op.create_table(
        "support_tickets",
        sa.Column(
            "id",
            pg.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "user_id",
            pg.UUID(as_uuid=True),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("title", sa.String(length=255), nullable=False),
        sa.Column("category", sa.String(length=50), nullable=False),
        sa.Column(
            "status",
            sa.String(length=32),
            nullable=False,
            server_default=sa.text("'open'"),
        ),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )
    op.create_index(
        "idx_support_tickets_user_id",
        "support_tickets",
        ["user_id"],
    )

    # ---------- support_messages ----------
    op.create_table(
        "support_messages",
        sa.Column(
            "id",
            pg.UUID(as_uuid=True),
            primary_key=True,
            server_default=sa.text("gen_random_uuid()"),
        ),
        sa.Column(
            "ticket_id",
            pg.UUID(as_uuid=True),
            sa.ForeignKey("support_tickets.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("sender_role", sa.String(length=32), nullable=False),
        sa.Column("sender_id", pg.UUID(as_uuid=True), nullable=True),
        sa.Column("text", sa.Text(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            nullable=False,
            server_default=sa.func.now(),
        ),
    )
    op.create_index(
        "idx_support_messages_ticket_id",
        "support_messages",
        ["ticket_id"],
    )


def downgrade():
    # порядок ОБРАТНЫЙ зависимостям
    op.drop_index("idx_support_messages_ticket_id", table_name="support_messages")
    op.drop_table("support_messages")

    op.drop_index("idx_support_tickets_user_id", table_name="support_tickets")
    op.drop_table("support_tickets")

    op.drop_index("idx_shift_photos_shift_id", table_name="shift_photos")
    op.drop_table("shift_photos")

    op.drop_index("idx_shifts_user_id", table_name="shifts")
    op.drop_table("shifts")

    op.drop_table("foreman_invites")
    op.drop_table("users")
