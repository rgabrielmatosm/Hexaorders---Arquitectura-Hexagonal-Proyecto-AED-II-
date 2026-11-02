-- HexaOrders v4: datos de envío del checkout y mensajes de la página de contacto.
-- Migración NO destructiva: solo crea tablas nuevas; no modifica ni borra datos existentes.

CREATE TABLE order_shipping (
    order_id      UUID PRIMARY KEY REFERENCES orders(id),
    recipient     VARCHAR(80)  NOT NULL CHECK (char_length(recipient) BETWEEN 2 AND 80),
    address       VARCHAR(160) NOT NULL CHECK (char_length(address) BETWEEN 5 AND 160),
    city          VARCHAR(60)  NOT NULL CHECK (char_length(city) BETWEEN 2 AND 60),
    phone         VARCHAR(20),
    instructions  VARCHAR(300)
);

CREATE TABLE contact_messages (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(80)   NOT NULL,
    email       VARCHAR(254)  NOT NULL,
    topic       VARCHAR(16)   NOT NULL CHECK (topic IN ('DEMO', 'SUPPORT', 'ACADEMIC', 'OTHER')),
    message     VARCHAR(2000) NOT NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE INDEX contact_messages_created_idx ON contact_messages (created_at DESC);
