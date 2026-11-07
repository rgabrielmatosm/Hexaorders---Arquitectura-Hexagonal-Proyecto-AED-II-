-- HexaOrders v5: eventos de integración (RabbitMQ) y consumidores idempotentes.
-- Migración NO destructiva: solo crea tablas nuevas.
--
-- Outbox transaccional: los eventos se derivan de order_status_history y delivery_changes, que ya
-- se escriben en la MISMA transacción que cada cambio de negocio. La marca de agua evita notificar
-- pedidos anteriores a esta versión.
CREATE TABLE event_relay_watermark (
    singleton               BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (singleton),
    order_history_after     BIGINT NOT NULL,
    delivery_changes_after  BIGINT NOT NULL,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO event_relay_watermark (order_history_after, delivery_changes_after)
SELECT COALESCE((SELECT max(id) FROM order_status_history), 0), COALESCE((SELECT max(id) FROM delivery_changes), 0);

-- Eventos confirmados por el broker (un evento sin fila aquí sigue pendiente de publicar).
CREATE TABLE outbox_published (
    event_id      VARCHAR(64) PRIMARY KEY,
    published_at  TIMESTAMPTZ NOT NULL
);

-- Eventos ya procesados por cada consumidor (descarta duplicados de la entrega at-least-once).
CREATE TABLE processed_events (
    event_id      VARCHAR(64) NOT NULL,
    consumer      VARCHAR(64) NOT NULL,
    processed_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (event_id, consumer)
);
