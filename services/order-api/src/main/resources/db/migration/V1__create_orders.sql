CREATE TABLE orders (
    id UUID PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL CHECK (customer_id ~ '^[A-Za-z0-9_-]{1,64}$'),
    amount NUMERIC(12, 2) NOT NULL CHECK (amount > 0 AND amount <= 1000000.00),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'USD'),
    risk_score SMALLINT NOT NULL CHECK (risk_score BETWEEN 0 AND 100),
    status VARCHAR(8) NOT NULL CHECK (status IN ('APPROVED', 'REVIEW')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT orders_status_matches_risk CHECK (
        (risk_score >= 70 AND status = 'REVIEW') OR (risk_score < 70 AND status = 'APPROVED')
    )
);

CREATE INDEX orders_created_at_idx ON orders (created_at DESC, id DESC);
