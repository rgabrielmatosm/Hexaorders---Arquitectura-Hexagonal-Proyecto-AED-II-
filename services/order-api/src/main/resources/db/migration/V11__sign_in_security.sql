-- POLYMER v7 · Inicio de sesión con Google, código de verificación por correo y «Recordarme 30 días».
-- Migración NO destructiva: columnas nuevas con valor por defecto y tablas nuevas. No modifica
-- contraseñas, roles, pedidos ni datos existentes. Las cuentas que ya existían quedan con el correo
-- verificado (no se bloquea a nadie); las cuentas nuevas con contraseña lo confirman con un código.
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_users ADD COLUMN IF NOT EXISTS two_factor BOOLEAN NOT NULL DEFAULT FALSE;

-- Identidades externas vinculadas (Google: «sub» del token de OpenID Connect).
CREATE TABLE IF NOT EXISTS user_identities (
    provider   VARCHAR(16)  NOT NULL CHECK (provider ~ '^[a-z]{2,16}$'),
    subject    VARCHAR(255) NOT NULL,
    user_id    BIGINT       NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    email      VARCHAR(254) NOT NULL,
    linked_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (provider, subject)
);
CREATE INDEX IF NOT EXISTS user_identities_user_idx ON user_identities (user_id);

-- Verificaciones por correo: solo el hash (BCrypt) del código de 6 dígitos.
CREATE TABLE IF NOT EXISTS verification_challenges (
    id            UUID PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    purpose       VARCHAR(24) NOT NULL CHECK (purpose IN ('EMAIL_VERIFICATION', 'SIGN_IN')),
    code_hash     VARCHAR(100) NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    attempts_left SMALLINT    NOT NULL CHECK (attempts_left BETWEEN 0 AND 5),
    sends         SMALLINT    NOT NULL CHECK (sends >= 1),
    last_sent_at  TIMESTAMPTZ NOT NULL,
    consumed      BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS verification_challenges_user_idx ON verification_challenges (user_id, created_at DESC);

-- Sesiones recordadas y dispositivos de confianza (30 días): solo el SHA-256 de la ficha.
CREATE TABLE IF NOT EXISTS device_tokens (
    kind         VARCHAR(20) NOT NULL CHECK (kind IN ('REMEMBERED_LOGIN', 'TRUSTED_DEVICE')),
    series       VARCHAR(40) NOT NULL,
    token_hash   CHAR(64)    NOT NULL,
    user_id      BIGINT      NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (kind, series)
);
CREATE INDEX IF NOT EXISTS device_tokens_user_idx ON device_tokens (kind, user_id);
