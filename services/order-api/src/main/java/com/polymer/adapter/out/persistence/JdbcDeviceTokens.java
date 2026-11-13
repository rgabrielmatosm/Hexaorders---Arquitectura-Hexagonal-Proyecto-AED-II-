package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.DeviceTokens;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcDeviceTokens implements DeviceTokens {
    private final JdbcTemplate jdbc;

    public JdbcDeviceTokens(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void store(Kind kind, String series, String tokenHash, long userId, Instant expiresAt) {
        Jdbc.run(() -> jdbc.update("INSERT INTO device_tokens (kind, series, token_hash, user_id, expires_at) VALUES (?, ?, ?, ?, ?)",
                kind.name(), series, tokenHash, userId, Timestamp.from(expiresAt)));
        // Limpieza oportunista: las fichas vencidas no sirven para nada.
        Jdbc.run(() -> jdbc.update("DELETE FROM device_tokens WHERE expires_at < now() - interval '1 day'"));
    }

    @Override
    public Optional<StoredToken> find(Kind kind, String series) {
        return Jdbc.run(() -> jdbc.query("SELECT series, token_hash, user_id, expires_at FROM device_tokens WHERE kind = ? AND series = ?",
                (rs, n) -> new StoredToken(rs.getString(1), rs.getString(2), rs.getLong(3), Jdbc.instant(rs.getTimestamp(4))), kind.name(), series))
                .stream().findFirst();
    }

    @Override
    public void rotate(Kind kind, String series, String tokenHash, Instant expiresAt) {
        Jdbc.run(() -> jdbc.update("UPDATE device_tokens SET token_hash = ?, expires_at = ?, last_used_at = now() WHERE kind = ? AND series = ?",
                tokenHash, Timestamp.from(expiresAt), kind.name(), series));
    }

    @Override
    public void delete(Kind kind, String series) {
        Jdbc.run(() -> jdbc.update("DELETE FROM device_tokens WHERE kind = ? AND series = ?", kind.name(), series));
    }

    @Override
    public void deleteAllFor(Kind kind, long userId) {
        Jdbc.run(() -> jdbc.update("DELETE FROM device_tokens WHERE kind = ? AND user_id = ?", kind.name(), userId));
    }
}
