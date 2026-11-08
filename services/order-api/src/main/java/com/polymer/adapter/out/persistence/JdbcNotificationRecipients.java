package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.NotificationRecipients;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcNotificationRecipients implements NotificationRecipients {
    private final JdbcTemplate jdbc;

    public JdbcNotificationRecipients(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<Recipient> forOrder(UUID orderId) {
        return Jdbc.run(() -> jdbc.query("SELECT u.display_name, u.email FROM orders o JOIN app_users u ON u.id = o.user_id "
                        + "WHERE o.id = ? AND o.channel = 'SHOP' AND u.enabled",
                (rs, n) -> new Recipient(rs.getString("display_name"), rs.getString("email")), orderId).stream().findFirst());
    }
}
