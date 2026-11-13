package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.AccountSecurity;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAccountSecurity implements AccountSecurity {
    private final JdbcTemplate jdbc;

    public JdbcAccountSecurity(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public SecurityState state(long userId) {
        List<SecurityState> rows = Jdbc.run(() -> jdbc.query("SELECT email_verified, two_factor, "
                + "EXISTS (SELECT 1 FROM user_identities i WHERE i.user_id = u.id AND i.provider = 'google') AS google "
                + "FROM app_users u WHERE id = ?", (rs, n) -> new SecurityState(rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3)), userId));
        return rows.isEmpty() ? new SecurityState(false, false, false) : rows.get(0);
    }

    @Override
    public void setEmailVerified(long userId, boolean verified) {
        Jdbc.run(() -> jdbc.update("UPDATE app_users SET email_verified = ? WHERE id = ?", verified, userId));
    }

    @Override
    public void setTwoFactor(long userId, boolean enabled) {
        Jdbc.run(() -> jdbc.update("UPDATE app_users SET two_factor = ? WHERE id = ?", enabled, userId));
    }

    @Override
    public Optional<Long> userForIdentity(String provider, String subject) {
        return Jdbc.run(() -> jdbc.query("SELECT user_id FROM user_identities WHERE provider = ? AND subject = ?",
                (rs, n) -> rs.getLong(1), provider, subject)).stream().findFirst();
    }

    @Override
    public void linkIdentity(long userId, String provider, String subject, String email) {
        Jdbc.run(() -> jdbc.update("INSERT INTO user_identities (provider, subject, user_id, email) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (provider, subject) DO UPDATE SET email = EXCLUDED.email", provider, subject, userId, email));
    }
}
