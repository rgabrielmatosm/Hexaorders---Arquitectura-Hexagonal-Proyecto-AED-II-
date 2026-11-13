-- POLYMER multilingüe: idioma preferido de cada usuario (para los correos de notificación).
-- Migración NO destructiva: tabla nueva; no modifica app_users, contraseñas, roles ni pedidos.
-- Sin fila = español (idioma predeterminado).
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id    BIGINT PRIMARY KEY REFERENCES app_users (id) ON DELETE CASCADE,
    language   VARCHAR(2)  NOT NULL DEFAULT 'es' CHECK (language IN ('es', 'en', 'fr', 'it', 'pt', 'de')),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
