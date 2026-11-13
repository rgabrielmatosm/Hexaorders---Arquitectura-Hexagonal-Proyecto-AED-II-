-- HexaOrders v6: tienda tecnológica. Migración NO destructiva: solo añade columnas, tablas,
-- restricciones más amplias y valores por defecto. No borra filas ni modifica pedidos existentes
-- (los pedidos anteriores conservan su moneda USD y sus importes).

-- 1) Categorías en dos niveles (categoría principal → subcategoría).
ALTER TABLE categories ALTER COLUMN name TYPE VARCHAR(80);
ALTER TABLE categories ADD COLUMN parent_id BIGINT REFERENCES categories (id);
ALTER TABLE categories ADD COLUMN position INTEGER NOT NULL DEFAULT 0 CHECK (position >= 0);
ALTER TABLE categories ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE categories ADD CONSTRAINT categories_not_own_parent CHECK (parent_id IS NULL OR parent_id <> id);
CREATE INDEX categories_parent_idx ON categories (parent_id, position);

-- 2) Ficha comercial y técnica del producto. Los precios de la tienda pasan a soles (PEN).
ALTER TABLE products ADD COLUMN brand VARCHAR(60) NOT NULL DEFAULT 'HexaOrders';
ALTER TABLE products ADD COLUMN model VARCHAR(80) NOT NULL DEFAULT '';
ALTER TABLE products ADD COLUMN long_description VARCHAR(4000) NOT NULL DEFAULT '';
ALTER TABLE products ADD COLUMN specs JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(specs) = 'array');
ALTER TABLE products ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(attributes) = 'object');
ALTER TABLE products ADD COLUMN compare_at_price NUMERIC(12, 2);
ALTER TABLE products ADD COLUMN commercial_status VARCHAR(16) NOT NULL DEFAULT 'STANDARD'
    CHECK (commercial_status IN ('STANDARD', 'NEW', 'FEATURED', 'BESTSELLER', 'CLEARANCE'));
ALTER TABLE products ADD CONSTRAINT products_compare_at_price_valid CHECK (compare_at_price IS NULL OR compare_at_price > price);
CREATE INDEX products_brand_idx ON products (brand);
CREATE INDEX products_attributes_idx ON products USING gin (attributes);

-- 3) Pedidos: la tienda vende en PEN; la API original (/api/orders) sigue aceptando solo USD (validado en Java).
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_currency_check;
ALTER TABLE orders ADD CONSTRAINT orders_currency_check CHECK (currency IN ('USD', 'PEN'));
-- Número de pedido visible para el cliente (HX-100001). Los pedidos existentes reciben uno por orden de creación.
CREATE SEQUENCE order_number_seq START WITH 100001;
ALTER TABLE orders ADD COLUMN order_number BIGINT UNIQUE;
UPDATE orders o SET order_number = numbered.n
FROM (SELECT id, nextval('order_number_seq') AS n FROM (SELECT id FROM orders ORDER BY created_at, id) ordered) numbered
WHERE numbered.id = o.id;
ALTER TABLE orders ALTER COLUMN order_number SET DEFAULT nextval('order_number_seq');
ALTER TABLE orders ALTER COLUMN order_number SET NOT NULL;
ALTER SEQUENCE order_number_seq OWNED BY orders.order_number;

-- 4) Promociones con vigencia, aplicables a categorías (incluye sus subcategorías) o a productos.
CREATE TABLE promotions (
    id               BIGSERIAL PRIMARY KEY,
    slug             VARCHAR(48) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    name             VARCHAR(80) NOT NULL,
    description      VARCHAR(300) NOT NULL DEFAULT '',
    discount_percent SMALLINT NOT NULL CHECK (discount_percent BETWEEN 1 AND 90),
    starts_at        TIMESTAMPTZ NOT NULL,
    ends_at          TIMESTAMPTZ NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT promotions_dates_valid CHECK (ends_at > starts_at)
);
CREATE TABLE promotion_targets (
    promotion_id BIGINT NOT NULL REFERENCES promotions (id) ON DELETE CASCADE,
    category_id  BIGINT REFERENCES categories (id),
    product_id   BIGINT REFERENCES products (id),
    CONSTRAINT promotion_targets_one_target CHECK ((category_id IS NULL) <> (product_id IS NULL)),
    CONSTRAINT promotion_targets_unique UNIQUE NULLS NOT DISTINCT (promotion_id, category_id, product_id)
);
CREATE INDEX promotion_targets_promotion_idx ON promotion_targets (promotion_id);

-- 5) Líneas con descuento: precio tachado y promoción aplicada (nulos en pedidos anteriores).
ALTER TABLE order_lines ADD COLUMN list_unit_price NUMERIC(12, 2);
ALTER TABLE order_lines ADD COLUMN promotion_id BIGINT REFERENCES promotions (id);

-- 6) Entrega, pago y totales del checkout (pedidos v6). No se guardan datos bancarios.
CREATE TABLE order_checkout (
    order_id        UUID PRIMARY KEY REFERENCES orders (id),
    delivery_method VARCHAR(16) NOT NULL CHECK (delivery_method IN ('STANDARD', 'EXPRESS', 'PICKUP')),
    payment_method  VARCHAR(20) CHECK (payment_method IN ('CASH_ON_DELIVERY', 'BANK_TRANSFER', 'PAY_IN_STORE')),
    subtotal        NUMERIC(14, 2) NOT NULL CHECK (subtotal > 0),
    savings         NUMERIC(14, 2) NOT NULL DEFAULT 0 CHECK (savings >= 0),
    shipping_cost   NUMERIC(10, 2) NOT NULL CHECK (shipping_cost >= 0),
    total           NUMERIC(14, 2) NOT NULL,
    CONSTRAINT order_checkout_total_matches CHECK (total = subtotal + shipping_cost)
);

-- 7) Carrito persistente del cliente (sobrevive a cierres de sesión y cambios de dispositivo).
CREATE TABLE cart_items (
    user_id    BIGINT NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products (id),
    quantity   INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    added_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id)
);

-- 8) Contacto: temas comerciales nuevos (los anteriores siguen siendo válidos).
ALTER TABLE contact_messages DROP CONSTRAINT IF EXISTS contact_messages_topic_check;
ALTER TABLE contact_messages ADD CONSTRAINT contact_messages_topic_check
    CHECK (topic IN ('DEMO', 'SUPPORT', 'ACADEMIC', 'OTHER', 'ORDERS', 'WARRANTY', 'SALES'));
