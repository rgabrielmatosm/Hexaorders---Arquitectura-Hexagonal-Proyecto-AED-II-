-- POLYMER v7 · Pagos en línea: tarjeta (inmediato), Yape y Plin (solicitud con QR válida 60 s) y boleta.
-- Migración NO destructiva: amplía los métodos de pago admitidos y crea tablas nuevas.
-- No se guardan números de tarjeta ni CVV: solo la marca y los 4 últimos dígitos.

-- 1) Métodos de pago del checkout: se añaden CARD, YAPE y PLIN.
DO $$
DECLARE constraint_name text;
BEGIN
    SELECT c.conname INTO constraint_name FROM pg_constraint c JOIN pg_class t ON t.oid = c.conrelid
     WHERE t.relname = 'order_checkout' AND c.contype = 'c' AND pg_get_constraintdef(c.oid) LIKE '%payment_method%';
    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE order_checkout DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;
ALTER TABLE order_checkout ADD CONSTRAINT order_checkout_payment_method_valid CHECK (payment_method IN
    ('CARD', 'YAPE', 'PLIN', 'CASH_ON_DELIVERY', 'BANK_TRANSFER', 'PAY_IN_STORE'));

-- 2) Intentos de pago en línea.
CREATE TABLE IF NOT EXISTS payments (
    id             UUID PRIMARY KEY,
    order_id       UUID          NOT NULL REFERENCES orders (id),
    method         VARCHAR(8)    NOT NULL CHECK (method IN ('CARD', 'YAPE', 'PLIN')),
    amount         NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    currency       CHAR(3)       NOT NULL,
    status         VARCHAR(10)   NOT NULL CHECK (status IN ('PENDING', 'PAID', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    created_at     TIMESTAMPTZ   NOT NULL,
    expires_at     TIMESTAMPTZ,
    paid_at        TIMESTAMPTZ,
    reference      VARCHAR(40),
    card_brand     VARCHAR(12),
    card_last4     CHAR(4)       CHECK (card_last4 IS NULL OR card_last4 ~ '^[0-9]{4}$'),
    failure_code   VARCHAR(32),
    receipt_series CHAR(4),
    receipt_number BIGINT,
    receipt_base   NUMERIC(14,2),
    receipt_igv    NUMERIC(14,2),
    CONSTRAINT payments_paid_has_receipt CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND receipt_number IS NOT NULL
        AND receipt_base + receipt_igv = amount)),
    CONSTRAINT payments_wallet_expires CHECK (method = 'CARD' OR expires_at IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS payments_order_idx ON payments (order_id, created_at DESC);
-- Nunca dos cobros aprobados del mismo pedido (aunque lleguen dos confirmaciones a la vez).
CREATE UNIQUE INDEX IF NOT EXISTS payments_one_paid_per_order ON payments (order_id) WHERE status = 'PAID';
CREATE UNIQUE INDEX IF NOT EXISTS payments_receipt_unique ON payments (receipt_series, receipt_number) WHERE receipt_number IS NOT NULL;

-- 3) Correlativo de las boletas (B001-00000001…).
CREATE SEQUENCE IF NOT EXISTS receipt_number_seq START 1;
