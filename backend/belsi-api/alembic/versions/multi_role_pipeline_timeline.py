"""BELSI 2.0.0 build3 — мульти-роль · pipeline партии · timeline · idle reasons по доменам

Revision ID: i9j0k1l2m3n4
Revises: h8i9j0k1l2m3
Create Date: 2026-05-11

Реализует 4 ключевых концепта брендбука:

1. МУЛЬТИ-РОЛЬ ДО 3 (раздел 06 ecosystem brandbook):
   - user_role_assignments (user_id, role, facility_id?, is_active, granted_*)
   - BEFORE INSERT триггер: лимит 3 активные роли на юзера
   - Backfill: для каждого users.role создаётся запись в user_role_assignments

2. ОБЪЕКТ КАК НИТЬ (раздел 02):
   - site_objects.type ENUM('production_facility', 'installation_target')
   - Углич → production_facility, остальные → installation_target

3. PIPELINE ПАРТИИ — СВЯЗКИ (раздел 03):
   - delivery_requests.batch_id FK → production_batches
   - driver_route_points.batch_id FK + delivery_request_id FK
   - Авто-статусы partition через триггер на route_points.status

4. IDLE REASONS ПО ДОМЕНАМ (раздел 13 ecosystem):
   - Таблица idle_reasons_catalog уже есть из production_batches миграции — наполняем.
   - Production 8, Installation 5, Logistics 5.

Все таблицы additive, downgrade чистый.
"""
from alembic import op


revision = "i9j0k1l2m3n4"
down_revision = "h8i9j0k1l2m3"
branch_labels = None
depends_on = None


def upgrade() -> None:
    # ════════════════════════════════════════════════════════════════
    # 1. МУЛЬТИ-РОЛЬ
    # ════════════════════════════════════════════════════════════════
    op.execute("""
        CREATE TABLE IF NOT EXISTS user_role_assignments (
            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
            user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
            role VARCHAR(50) NOT NULL,
            facility_id UUID REFERENCES site_objects(id) ON DELETE SET NULL,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            is_primary BOOLEAN NOT NULL DEFAULT FALSE,
            granted_by UUID REFERENCES users(id) ON DELETE SET NULL,
            granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
            revoked_at TIMESTAMP WITH TIME ZONE,
            CHECK (role IN ('installer','foreman','coordinator','curator',
                            'driver','logistician',
                            'production_chief','senior_worker','worker','supplier','engineer'))
        )
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_ura_user ON user_role_assignments(user_id, is_active)")
    op.execute("CREATE INDEX IF NOT EXISTS idx_ura_role ON user_role_assignments(role) WHERE is_active = TRUE")
    op.execute("""
        CREATE UNIQUE INDEX IF NOT EXISTS uq_ura_user_role_facility
        ON user_role_assignments(user_id, role, COALESCE(facility_id, '00000000-0000-0000-0000-000000000000'::uuid))
        WHERE is_active = TRUE
    """)

    # Триггер лимита 3 активные роли (брендбук жёстко прописывает)
    op.execute("""
        CREATE OR REPLACE FUNCTION enforce_max_3_roles() RETURNS TRIGGER AS $$
        BEGIN
            IF NEW.is_active THEN
                IF (SELECT COUNT(*) FROM user_role_assignments
                    WHERE user_id = NEW.user_id AND is_active = TRUE AND id != COALESCE(NEW.id, '00000000-0000-0000-0000-000000000000'::uuid)) >= 3 THEN
                    RAISE EXCEPTION 'User % already has 3 active roles. Revoke one before granting another.', NEW.user_id;
                END IF;
            END IF;
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
    """)
    op.execute("""
        DROP TRIGGER IF EXISTS trg_max_3_roles ON user_role_assignments;
        CREATE TRIGGER trg_max_3_roles
            BEFORE INSERT OR UPDATE ON user_role_assignments
            FOR EACH ROW EXECUTE FUNCTION enforce_max_3_roles();
    """)

    # Backfill из существующих users.role — каждый юзер получает primary запись
    op.execute("""
        INSERT INTO user_role_assignments (user_id, role, is_active, is_primary, granted_at)
        SELECT id, role, TRUE, TRUE, COALESCE(created_at, NOW())
        FROM users
        WHERE role IS NOT NULL AND role != ''
        ON CONFLICT DO NOTHING
    """)

    # ════════════════════════════════════════════════════════════════
    # 2. ОБЪЕКТ КАК НИТЬ — site_objects.type
    # ════════════════════════════════════════════════════════════════
    op.execute("""
        DO $$ BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_name='site_objects' AND column_name='object_type_v2'
            ) THEN
                ALTER TABLE site_objects ADD COLUMN object_type_v2 VARCHAR(30)
                    NOT NULL DEFAULT 'installation_target'
                    CHECK (object_type_v2 IN ('production_facility', 'installation_target'));
            END IF;
        END $$;
    """)
    # Углич помечаем как production_facility
    op.execute("""
        UPDATE site_objects SET object_type_v2 = 'production_facility'
        WHERE name ILIKE '%углич%' OR name ILIKE '%фабрика%';
    """)
    op.execute("CREATE INDEX IF NOT EXISTS idx_site_objects_type ON site_objects(object_type_v2)")

    # ════════════════════════════════════════════════════════════════
    # 3. PIPELINE ПАРТИИ — связки FK
    # ════════════════════════════════════════════════════════════════
    op.execute("""
        DO $$ BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_name='delivery_requests' AND column_name='batch_id'
            ) THEN
                ALTER TABLE delivery_requests
                    ADD COLUMN batch_id UUID REFERENCES production_batches(id) ON DELETE SET NULL;
                CREATE INDEX idx_delivery_requests_batch ON delivery_requests(batch_id) WHERE batch_id IS NOT NULL;
            END IF;
        END $$;
    """)
    op.execute("""
        DO $$ BEGIN
            IF NOT EXISTS (
                SELECT 1 FROM information_schema.columns
                WHERE table_name='driver_route_points' AND column_name='batch_id'
            ) THEN
                ALTER TABLE driver_route_points
                    ADD COLUMN batch_id UUID REFERENCES production_batches(id) ON DELETE SET NULL,
                    ADD COLUMN delivery_request_id UUID REFERENCES delivery_requests(id) ON DELETE SET NULL;
                CREATE INDEX idx_route_points_batch ON driver_route_points(batch_id) WHERE batch_id IS NOT NULL;
                CREATE INDEX idx_route_points_request ON driver_route_points(delivery_request_id) WHERE delivery_request_id IS NOT NULL;
            END IF;
        END $$;
    """)

    # Триггер каскада статуса партии: когда route_point.status меняется → партия меняет статус
    op.execute("""
        CREATE OR REPLACE FUNCTION cascade_batch_status_from_route() RETURNS TRIGGER AS $$
        DECLARE
            target_status VARCHAR;
        BEGIN
            IF NEW.batch_id IS NULL THEN RETURN NEW; END IF;
            -- arrived = водитель приехал к точке партии
            IF NEW.status = 'arrived' AND (OLD IS NULL OR OLD.status != 'arrived') THEN
                target_status := 'in_route';
            -- delivered = доставлена бригаде
            ELSIF NEW.status = 'delivered' AND (OLD IS NULL OR OLD.status != 'delivered') THEN
                target_status := 'delivered';
            ELSE
                RETURN NEW;
            END IF;
            -- Обновляем партию (только если новый статус валидный шаг вперёд)
            UPDATE production_batches
            SET status = target_status, updated_at = NOW()
            WHERE id = NEW.batch_id
              AND status IN ('ready_to_ship', 'in_route');  -- защита от обратных переходов
            -- Лог изменения
            INSERT INTO batch_status_history (batch_id, from_status, to_status, changed_by, reason)
            SELECT NEW.batch_id, status, target_status, NULL,
                   'auto: route_point ' || NEW.id || ' → ' || NEW.status
            FROM production_batches WHERE id = NEW.batch_id;
            RETURN NEW;
        END;
        $$ LANGUAGE plpgsql;
    """)
    op.execute("""
        DROP TRIGGER IF EXISTS trg_cascade_batch_status ON driver_route_points;
        CREATE TRIGGER trg_cascade_batch_status
            AFTER UPDATE OF status ON driver_route_points
            FOR EACH ROW EXECUTE FUNCTION cascade_batch_status_from_route();
    """)

    # ════════════════════════════════════════════════════════════════
    # 4. IDLE_REASONS по доменам — наполнение
    # ════════════════════════════════════════════════════════════════
    op.execute("""
        DO $$ BEGIN
            IF NOT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name='idle_reasons_catalog') THEN
                CREATE TABLE idle_reasons_catalog (
                    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                    domain VARCHAR(20) NOT NULL CHECK (domain IN ('production', 'installation', 'logistics')),
                    code VARCHAR(50) NOT NULL,
                    label_ru VARCHAR(200) NOT NULL,
                    sort_order INT NOT NULL DEFAULT 0,
                    is_active BOOLEAN NOT NULL DEFAULT TRUE,
                    UNIQUE (domain, code)
                );
            END IF;
        END $$;
    """)
    # Production (8 причин)
    op.execute("""
        INSERT INTO idle_reasons_catalog (domain, code, label_ru, sort_order) VALUES
          ('production', 'wait_materials',    'Жду материалы',                  10),
          ('production', 'no_work',           'Нет работы',                     20),
          ('production', 'machine_breakdown', 'Поломка станка',                 30),
          ('production', 'wait_supplier',     'Жду комплектатора',              40),
          ('production', 'wait_engineer',     'Жду чертежи от инженера',        50),
          ('production', 'wait_qc',           'Жду ОТК / приёмку',              60),
          ('production', 'power_outage',      'Отключение электричества',       70),
          ('production', 'other',             'Другое',                         99)
        ON CONFLICT (domain, code) DO UPDATE SET label_ru = EXCLUDED.label_ru, sort_order = EXCLUDED.sort_order;
    """)
    # Installation (5)
    op.execute("""
        INSERT INTO idle_reasons_catalog (domain, code, label_ru, sort_order) VALUES
          ('installation', 'wait_materials',  'Жду материалы / доставку',       10),
          ('installation', 'wait_foreman',    'Жду бригадира',                  20),
          ('installation', 'tool_broken',     'Поломка инструмента',            30),
          ('installation', 'weather',         'Погодные условия',               40),
          ('installation', 'other',           'Другое',                         99)
        ON CONFLICT (domain, code) DO UPDATE SET label_ru = EXCLUDED.label_ru, sort_order = EXCLUDED.sort_order;
    """)
    # Logistics (5)
    op.execute("""
        INSERT INTO idle_reasons_catalog (domain, code, label_ru, sort_order) VALUES
          ('logistics', 'vehicle_breakdown', 'Поломка ТС',                     10),
          ('logistics', 'wait_loading',     'Ожидание загрузки',               20),
          ('logistics', 'wait_unloading',   'Ожидание выгрузки',               30),
          ('logistics', 'traffic',          'Пробка / ДТП',                    40),
          ('logistics', 'other',            'Другое',                          99)
        ON CONFLICT (domain, code) DO UPDATE SET label_ru = EXCLUDED.label_ru, sort_order = EXCLUDED.sort_order;
    """)


def downgrade() -> None:
    op.execute("DROP TRIGGER IF EXISTS trg_cascade_batch_status ON driver_route_points")
    op.execute("DROP FUNCTION IF EXISTS cascade_batch_status_from_route()")
    op.execute("DROP TRIGGER IF EXISTS trg_max_3_roles ON user_role_assignments")
    op.execute("DROP FUNCTION IF EXISTS enforce_max_3_roles()")
    op.execute("DROP TABLE IF EXISTS user_role_assignments CASCADE")
    op.execute("ALTER TABLE site_objects DROP COLUMN IF EXISTS object_type_v2")
    op.execute("ALTER TABLE delivery_requests DROP COLUMN IF EXISTS batch_id")
    op.execute("ALTER TABLE driver_route_points DROP COLUMN IF EXISTS batch_id, DROP COLUMN IF EXISTS delivery_request_id")
    op.execute("DELETE FROM idle_reasons_catalog WHERE domain IN ('production', 'installation', 'logistics')")
