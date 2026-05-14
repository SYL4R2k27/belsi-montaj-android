"""Driver/Logistician domain: маршруты, точки, заявки на доставку

Revision ID: h8i9j0k1l2m3
Revises: g7h8i9j0k1l2
Create Date: 2026-05-11

FIX(2026-05-11) BELSI 2.0.0: 3 таблицы для logist/driver:
  - driver_routes (маршрут на день, привязан к водителю)
  - driver_route_points (точки маршрута: pickup/delivery/transit/return)
  - delivery_requests (заявки координаторов на доставку, попадают в маршрут)

Все таблицы additive — не влияют на 1.2.5 совместимость.
"""
from alembic import op


revision = "h8i9j0k1l2m3"
down_revision = "g7h8i9j0k1l2"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # === driver_routes: маршрут на день ===
    op.execute("""
        CREATE TABLE IF NOT EXISTS driver_routes (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            driver_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
            logistician_id UUID REFERENCES users(id) ON DELETE SET NULL,
            planned_date DATE NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT 'planned'
                CHECK (status IN ('planned', 'active', 'completed', 'cancelled')),
            started_at TIMESTAMP WITH TIME ZONE,
            completed_at TIMESTAMP WITH TIME ZONE,
            total_points INT NOT NULL DEFAULT 0,
            completed_points INT NOT NULL DEFAULT 0,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_driver_routes_driver_date ON driver_routes(driver_id, planned_date DESC)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_driver_routes_status ON driver_routes(status) WHERE status IN ('planned', 'active')")
    op.execute("CREATE INDEX IF NOT EXISTS idx_driver_routes_logist ON driver_routes(logistician_id, planned_date DESC)")

    # === driver_route_points: точки маршрута ===
    op.execute("""
        CREATE TABLE IF NOT EXISTS driver_route_points (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            route_id UUID NOT NULL REFERENCES driver_routes(id) ON DELETE CASCADE,
            seq INT NOT NULL,
            scheduled_time VARCHAR(8),
            point_type VARCHAR(20) NOT NULL
                CHECK (point_type IN ('pickup', 'delivery', 'transit', 'return')),
            address TEXT NOT NULL,
            site_object_id UUID REFERENCES site_objects(id) ON DELETE SET NULL,
            latitude DOUBLE PRECISION,
            longitude DOUBLE PRECISION,
            cargo TEXT,
            status VARCHAR(20) NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'arrived', 'delivered', 'skipped')),
            arrived_at TIMESTAMP WITH TIME ZONE,
            delivered_at TIMESTAMP WITH TIME ZONE,
            photo_url TEXT,
            skip_reason TEXT,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_route_points_route_seq ON driver_route_points(route_id, seq)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_route_points_status ON driver_route_points(status)")
    op.execute("CREATE UNIQUE INDEX IF NOT EXISTS uq_route_points_seq ON driver_route_points(route_id, seq)")

    # === delivery_requests: заявки на доставку (от координатора/куратора) ===
    op.execute("""
        CREATE TABLE IF NOT EXISTS delivery_requests (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
            site_object_id UUID REFERENCES site_objects(id) ON DELETE SET NULL,
            object_name_snapshot VARCHAR(200),
            cargo TEXT NOT NULL,
            need_by_time VARCHAR(8),
            need_by_date DATE NOT NULL DEFAULT CURRENT_DATE,
            priority VARCHAR(10) NOT NULL DEFAULT 'normal'
                CHECK (priority IN ('low', 'normal', 'high', 'urgent')),
            status VARCHAR(20) NOT NULL DEFAULT 'pending'
                CHECK (status IN ('pending', 'assigned', 'in_transit', 'delivered', 'cancelled')),
            assigned_route_id UUID REFERENCES driver_routes(id) ON DELETE SET NULL,
            assigned_point_id UUID REFERENCES driver_route_points(id) ON DELETE SET NULL,
            assigned_at TIMESTAMP WITH TIME ZONE,
            delivered_at TIMESTAMP WITH TIME ZONE,
            notes TEXT,
            created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_delivery_requests_status_date ON delivery_requests(status, need_by_date DESC)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_delivery_requests_creator ON delivery_requests(created_by, need_by_date DESC)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_delivery_requests_route ON delivery_requests(assigned_route_id) WHERE assigned_route_id IS NOT NULL")


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS delivery_requests CASCADE")
    op.execute("DROP TABLE IF EXISTS driver_route_points CASCADE")
    op.execute("DROP TABLE IF EXISTS driver_routes CASCADE")
