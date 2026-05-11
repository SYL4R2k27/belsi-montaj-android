"""Production batches + pipeline + factory shifts + idle reasons by domain

Revision ID: d4e5f6g7h8i9
Revises: c3d4e5f6g7h8
Create Date: 2026-05-05

Назначение:
- Сущность Партия (production_batches): создаётся Начальником производства,
  проходит pipeline draft → in_production → ready_to_ship → in_route →
  delivered → installed.
- batch_status_history: audit log смен статусов.
- site_objects.type: production_facility | installation_target.
- shifts: lunch_seconds + break_seconds (раздельные счётчики для производства).
- shifts.facility_id: какая фабрика для производственных смен.
- shift_idle_reason_catalog: справочник причин простоя по доменам.

Backward compat 1.2.5:
- Все новые таблицы — изолированные, не задевают старые.
- ALTER TABLE shifts: только ADD COLUMN с дефолтами.
- ALTER site_objects: ADD COLUMN с default.
- Старые клиенты продолжают работать как раньше.
"""
from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects.postgresql import UUID, JSONB

revision = "d4e5f6g7h8i9"
down_revision = "c3d4e5f6g7h8"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ───────── 1. site_objects.type ─────────
    # Различаем фабрики (Углич) и целевые объекты (школы)
    op.execute(
        "ALTER TABLE site_objects "
        "ADD COLUMN IF NOT EXISTS object_type TEXT NOT NULL DEFAULT 'installation_target'"
    )
    op.execute(
        "CREATE INDEX IF NOT EXISTS idx_site_objects_type ON site_objects(object_type)"
    )

    # ───────── 2. production_batches ─────────
    op.create_table(
        "production_batches",
        sa.Column("id", UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("item_count", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("source_facility_id", UUID(as_uuid=True), sa.ForeignKey("site_objects.id"), nullable=False),
        sa.Column("target_object_id", UUID(as_uuid=True), sa.ForeignKey("site_objects.id"), nullable=True),
        sa.Column(
            "status", sa.Text(), nullable=False, server_default="draft",
            comment="draft / in_production / ready_to_ship / in_route / delivered / installed",
        ),
        sa.Column("priority", sa.Text(), nullable=False, server_default="normal"),
        sa.Column("deadline", sa.DateTime(timezone=True), nullable=True),
        sa.Column("created_by", UUID(as_uuid=True), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("responsible_user_id", UUID(as_uuid=True), sa.ForeignKey("users.id"), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("meta", JSONB(), nullable=True),
    )
    op.execute("CREATE INDEX idx_batches_status ON production_batches(status)")
    op.execute("CREATE INDEX idx_batches_facility ON production_batches(source_facility_id)")
    op.execute("CREATE INDEX idx_batches_target ON production_batches(target_object_id)")
    op.execute("CREATE INDEX idx_batches_deadline ON production_batches(deadline) WHERE deadline IS NOT NULL")

    # ───────── 3. batch_status_history (audit log) ─────────
    op.create_table(
        "batch_status_history",
        sa.Column("id", UUID(as_uuid=True), primary_key=True, server_default=sa.text("gen_random_uuid()")),
        sa.Column("batch_id", UUID(as_uuid=True), sa.ForeignKey("production_batches.id", ondelete="CASCADE"), nullable=False),
        sa.Column("from_status", sa.Text(), nullable=True),
        sa.Column("to_status", sa.Text(), nullable=False),
        sa.Column("changed_by", UUID(as_uuid=True), sa.ForeignKey("users.id"), nullable=False),
        sa.Column("changed_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("comment", sa.Text(), nullable=True),
    )
    op.execute("CREATE INDEX idx_batch_history_batch ON batch_status_history(batch_id)")

    # ───────── 4. shifts: раздельные счётчики ─────────
    op.execute("ALTER TABLE shifts ADD COLUMN IF NOT EXISTS lunch_seconds BIGINT DEFAULT 0")
    op.execute("ALTER TABLE shifts ADD COLUMN IF NOT EXISTS break_seconds BIGINT DEFAULT 0")
    # facility_id для производственных смен (какая фабрика — null для монтажа)
    op.execute(
        "ALTER TABLE shifts ADD COLUMN IF NOT EXISTS facility_id UUID "
        "REFERENCES site_objects(id) ON DELETE SET NULL"
    )
    # domain — какому домену принадлежит смена (production / installation / logistics)
    op.execute(
        "ALTER TABLE shifts ADD COLUMN IF NOT EXISTS domain TEXT DEFAULT 'installation'"
    )

    # ───────── 5. shift_idle_reason_catalog ─────────
    # Справочник причин по домену, чтобы UI/API мог отдать список под роль
    op.create_table(
        "shift_idle_reason_catalog",
        sa.Column("id", sa.Integer(), primary_key=True, autoincrement=True),
        sa.Column("domain", sa.Text(), nullable=False),
        sa.Column("code", sa.Text(), nullable=False),
        sa.Column("label", sa.Text(), nullable=False),
        sa.Column("position", sa.Integer(), nullable=False, server_default="0"),
        sa.Column("active", sa.Boolean(), nullable=False, server_default=sa.text("true")),
    )
    op.execute(
        "CREATE UNIQUE INDEX uq_idle_domain_code ON shift_idle_reason_catalog(domain, code)"
    )

    # Заполняем дефолтные причины
    op.execute("""
        INSERT INTO shift_idle_reason_catalog (domain, code, label, position) VALUES
        -- Монтаж
        ('installation', 'wait_materials', 'Ожидание материалов', 1),
        ('installation', 'wait_tools', 'Ожидание инструмента', 2),
        ('installation', 'tech_problems', 'Технические проблемы', 3),
        ('installation', 'weather', 'Погодные условия', 4),
        ('installation', 'wait_foreman', 'Ожидание бригадира', 5),
        -- Логистика
        ('logistics', 'vehicle_breakdown', 'Поломка ТС', 1),
        ('logistics', 'wait_loading', 'Ожидание загрузки', 2),
        ('logistics', 'wait_unloading', 'Ожидание выгрузки', 3),
        ('logistics', 'traffic', 'Пробка / ДТП', 4),
        ('logistics', 'other', 'Другое', 99),
        -- Производство
        ('production', 'wait_materials', 'Жду материалы', 1),
        ('production', 'no_work', 'Нет работы', 2),
        ('production', 'machine_breakdown', 'Поломка станка', 3),
        ('production', 'wait_supplier', 'Жду комплектатора', 4),
        ('production', 'wait_drawings', 'Жду чертежи от инженера', 5),
        ('production', 'wait_qa', 'Жду ОТК / приёмку', 6),
        ('production', 'no_power', 'Отключение электричества', 7),
        ('production', 'other', 'Другое', 99)
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS shift_idle_reason_catalog")
    op.execute("ALTER TABLE shifts DROP COLUMN IF EXISTS domain")
    op.execute("ALTER TABLE shifts DROP COLUMN IF EXISTS facility_id")
    op.execute("ALTER TABLE shifts DROP COLUMN IF EXISTS break_seconds")
    op.execute("ALTER TABLE shifts DROP COLUMN IF EXISTS lunch_seconds")
    op.execute("DROP TABLE IF EXISTS batch_status_history")
    op.execute("DROP TABLE IF EXISTS production_batches")
    op.execute("DROP INDEX IF EXISTS idx_site_objects_type")
    op.execute("ALTER TABLE site_objects DROP COLUMN IF EXISTS object_type")
