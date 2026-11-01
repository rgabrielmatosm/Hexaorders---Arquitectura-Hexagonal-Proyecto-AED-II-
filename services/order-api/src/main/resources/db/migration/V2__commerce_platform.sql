-- V2 · Plataforma comercial. Migración incremental y no destructiva:
--  * La tabla orders y sus columnas originales se conservan intactas (mismo contrato de la API original).
--  * Los pedidos existentes reciben channel='LAB' y un fulfillment_status derivado de su decisión de riesgo.
--  * Se añaden usuarios, roles, catálogo, líneas, historial, entregas, incidencias y auditoría.

CREATE TABLE app_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE CHECK (username ~ '^[A-Za-z0-9_-]{3,32}$'),
    display_name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE app_user_roles (
    user_id BIGINT NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    role VARCHAR(24) NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN', 'ORDER_OPERATOR', 'LOGISTICS_OPERATOR', 'OBSERVABILITY_VIEWER')),
    PRIMARY KEY (user_id, role)
);

ALTER TABLE orders ADD COLUMN channel VARCHAR(8) NOT NULL DEFAULT 'LAB' CHECK (channel IN ('LAB', 'SHOP'));
ALTER TABLE orders ADD COLUMN fulfillment_status VARCHAR(24);
UPDATE orders SET fulfillment_status = CASE status WHEN 'REVIEW' THEN 'RISK_REVIEW' ELSE 'RISK_APPROVED' END;
ALTER TABLE orders ALTER COLUMN fulfillment_status SET NOT NULL;
ALTER TABLE orders ADD CONSTRAINT orders_fulfillment_status_valid CHECK (fulfillment_status IN (
    'CREATED', 'RISK_APPROVED', 'RISK_REVIEW', 'CONFIRMED', 'PREPARING', 'READY_FOR_DISPATCH',
    'IN_TRANSIT', 'DELIVERED', 'REJECTED', 'CANCELLED'));
ALTER TABLE orders ADD COLUMN version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE orders ADD COLUMN idempotency_key VARCHAR(64) UNIQUE;
ALTER TABLE orders ADD COLUMN user_id BIGINT REFERENCES app_users (id);
ALTER TABLE orders ADD CONSTRAINT orders_shop_has_user CHECK (channel = 'LAB' OR user_id IS NOT NULL);
CREATE INDEX orders_user_idx ON orders (user_id, created_at DESC);
CREATE INDEX orders_fulfillment_idx ON orders (fulfillment_status, created_at DESC);

CREATE TABLE categories (
    id BIGSERIAL PRIMARY KEY,
    slug VARCHAR(48) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name VARCHAR(60) NOT NULL
);

CREATE TABLE products (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(32) NOT NULL UNIQUE CHECK (sku ~ '^[A-Z0-9-]{3,32}$'),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(1000) NOT NULL DEFAULT '',
    category_id BIGINT NOT NULL REFERENCES categories (id),
    price NUMERIC(12, 2) NOT NULL CHECK (price > 0 AND price <= 100000),
    stock INTEGER NOT NULL CHECK (stock >= 0),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    hue SMALLINT NOT NULL DEFAULT 260 CHECK (hue BETWEEN 0 AND 359),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX products_category_idx ON products (category_id);

CREATE TABLE order_lines (
    order_id UUID NOT NULL REFERENCES orders (id),
    line_no SMALLINT NOT NULL CHECK (line_no > 0),
    product_id BIGINT NOT NULL REFERENCES products (id),
    product_name VARCHAR(120) NOT NULL,
    unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price > 0),
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    line_total NUMERIC(14, 2) NOT NULL,
    PRIMARY KEY (order_id, line_no),
    CONSTRAINT order_lines_total_matches CHECK (line_total = unit_price * quantity)
);

CREATE TABLE order_status_history (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders (id),
    from_status VARCHAR(24),
    to_status VARCHAR(24) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    note VARCHAR(500),
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX order_status_history_order_idx ON order_status_history (order_id, id);

-- Historial inicial de los pedidos que ya existían: su estado procede de la decisión de riesgo original.
INSERT INTO order_status_history (order_id, from_status, to_status, actor, note, changed_at)
SELECT id, NULL, fulfillment_status, 'migration-v2', 'Estado derivado de la decisión de riesgo original', created_at FROM orders;

CREATE TABLE deliveries (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders (id),
    assignee_id BIGINT NOT NULL REFERENCES app_users (id),
    window_start TIMESTAMPTZ NOT NULL,
    window_end TIMESTAMPTZ NOT NULL,
    notes VARCHAR(500),
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT deliveries_window_valid CHECK (window_end > window_start AND window_end - window_start <= INTERVAL '8 hours')
);
CREATE INDEX deliveries_assignee_idx ON deliveries (assignee_id, window_start);
CREATE INDEX deliveries_window_idx ON deliveries (window_start);

CREATE TABLE delivery_changes (
    id BIGSERIAL PRIMARY KEY,
    delivery_id BIGINT NOT NULL REFERENCES deliveries (id),
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL CHECK (action IN ('CREATED', 'RESCHEDULED')),
    old_start TIMESTAMPTZ,
    old_end TIMESTAMPTZ,
    old_assignee VARCHAR(32),
    new_start TIMESTAMPTZ NOT NULL,
    new_end TIMESTAMPTZ NOT NULL,
    new_assignee VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    changed_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX delivery_changes_delivery_idx ON delivery_changes (delivery_id, id);

CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    order_id UUID REFERENCES orders (id),
    category VARCHAR(16) NOT NULL CHECK (category IN ('DAMAGED', 'DELAY', 'ADDRESS', 'STOCK', 'OTHER')),
    description VARCHAR(1000) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    reported_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    resolution VARCHAR(500)
);
CREATE INDEX incidents_status_idx ON incidents (status, created_at DESC);

CREATE TABLE audit_events (
    id BIGSERIAL PRIMARY KEY,
    actor VARCHAR(64) NOT NULL,
    action VARCHAR(40) NOT NULL,
    target VARCHAR(120) NOT NULL,
    details VARCHAR(1000) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_created_idx ON audit_events (created_at DESC, id DESC);
