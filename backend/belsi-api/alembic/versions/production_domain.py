"""Production domain: brigades, materials, engineer tasks

Revision ID: e5f6g7h8i9j0
Revises: d4e5f6g7h8i9
Create Date: 2026-05-06

Назначение:
- Бригады производства (один Старший работник + N Работников)
- Материалы (каталог + остатки на фабриках + заявки от снабженцев)
- Инженерные задачи (специальные задания для Engineer-роли)

Backward compat: всё аддитивно, безопасно для прода 1.2.5.
"""
from alembic import op
import sqlalchemy as sa


revision = "e5f6g7h8i9j0"
down_revision = "d4e5f6g7h8i9"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ────────────────────────────────────────────────────────────────
    # Бригады
    # ────────────────────────────────────────────────────────────────
    op.execute("""
        CREATE TABLE IF NOT EXISTS brigades (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            name TEXT NOT NULL,
            facility_id UUID NOT NULL REFERENCES site_objects(id),
            senior_worker_id UUID REFERENCES users(id),
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_brigades_facility ON brigades(facility_id)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_brigades_senior ON brigades(senior_worker_id)")

    op.execute("""
        CREATE TABLE IF NOT EXISTS brigade_members (
            brigade_id UUID NOT NULL REFERENCES brigades(id) ON DELETE CASCADE,
            user_id UUID NOT NULL REFERENCES users(id),
            role_in_brigade TEXT NOT NULL DEFAULT 'worker',
            joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            PRIMARY KEY (brigade_id, user_id)
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_brigade_members_user ON brigade_members(user_id)")

    # ────────────────────────────────────────────────────────────────
    # Материалы
    # ────────────────────────────────────────────────────────────────
    op.execute("""
        CREATE TABLE IF NOT EXISTS materials_catalog (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            code TEXT NOT NULL UNIQUE,
            name TEXT NOT NULL,
            unit TEXT NOT NULL DEFAULT 'шт',
            category TEXT,
            min_stock INTEGER NOT NULL DEFAULT 0,
            active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_materials_active ON materials_catalog(active) WHERE active")

    op.execute("""
        CREATE TABLE IF NOT EXISTS materials_inventory (
            facility_id UUID NOT NULL REFERENCES site_objects(id),
            material_id UUID NOT NULL REFERENCES materials_catalog(id),
            quantity INTEGER NOT NULL DEFAULT 0,
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            PRIMARY KEY (facility_id, material_id)
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_inventory_facility ON materials_inventory(facility_id)")

    op.execute("""
        CREATE TABLE IF NOT EXISTS material_orders (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            facility_id UUID NOT NULL REFERENCES site_objects(id),
            material_id UUID NOT NULL REFERENCES materials_catalog(id),
            quantity_requested INTEGER NOT NULL,
            quantity_delivered INTEGER DEFAULT 0,
            status TEXT NOT NULL DEFAULT 'pending',
            requested_by UUID NOT NULL REFERENCES users(id),
            approved_by UUID REFERENCES users(id),
            supplier_id UUID REFERENCES users(id),
            note TEXT,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_material_orders_status ON material_orders(status)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_material_orders_facility ON material_orders(facility_id)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_material_orders_supplier ON material_orders(supplier_id)")

    # ────────────────────────────────────────────────────────────────
    # Инженерные задачи (отдельно от tasks, потому что у них своя логика)
    # ────────────────────────────────────────────────────────────────
    op.execute("""
        CREATE TABLE IF NOT EXISTS engineer_tasks (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            batch_id UUID REFERENCES production_batches(id),
            facility_id UUID REFERENCES site_objects(id),
            type TEXT NOT NULL DEFAULT 'general',
            title TEXT NOT NULL,
            description TEXT,
            assigned_to UUID REFERENCES users(id),
            status TEXT NOT NULL DEFAULT 'open',
            priority TEXT NOT NULL DEFAULT 'normal',
            due_at TIMESTAMPTZ,
            completed_at TIMESTAMPTZ,
            created_by UUID NOT NULL REFERENCES users(id),
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_engineer_tasks_assigned ON engineer_tasks(assigned_to)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_engineer_tasks_status ON engineer_tasks(status)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_engineer_tasks_batch ON engineer_tasks(batch_id)")

    # ────────────────────────────────────────────────────────────────
    # Seed: базовые материалы для Уголичской фабрики (демо).
    # Реальные данные могут быть импортированы позже.
    # ────────────────────────────────────────────────────────────────
    op.execute("""
        INSERT INTO materials_catalog (code, name, unit, category, min_stock) VALUES
        ('M-001', 'ЛДСП 16мм', 'м²', 'панели', 50),
        ('M-002', 'МДФ 19мм', 'м²', 'панели', 30),
        ('M-003', 'Кромка ПВХ 0.4мм', 'м', 'кромка', 200),
        ('M-004', 'Кромка ПВХ 2мм', 'м', 'кромка', 100),
        ('M-005', 'Конфирмат 7×50', 'шт', 'крепеж', 500),
        ('M-006', 'Заглушка эксцентрика', 'шт', 'крепеж', 300),
        ('M-007', 'Петля накладная', 'шт', 'фурнитура', 100),
        ('M-008', 'Направляющая 450мм', 'компл', 'фурнитура', 50),
        ('M-009', 'Ручка-рейлинг', 'шт', 'фурнитура', 100),
        ('M-010', 'Стяжка минификс', 'шт', 'крепеж', 400)
        ON CONFLICT (code) DO NOTHING
    """)


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS engineer_tasks CASCADE")
    op.execute("DROP TABLE IF EXISTS material_orders CASCADE")
    op.execute("DROP TABLE IF EXISTS materials_inventory CASCADE")
    op.execute("DROP TABLE IF EXISTS materials_catalog CASCADE")
    op.execute("DROP TABLE IF EXISTS brigade_members CASCADE")
    op.execute("DROP TABLE IF EXISTS brigades CASCADE")
