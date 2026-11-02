package com.hexaorders.adapter.out.persistence;

import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.domain.audit.AuditEvent;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcAuditLog implements AuditLog {
    private final JdbcTemplate jdbc;

    public JdbcAuditLog(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static String limit(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    @Override
    public void record(String actor, String action, String target, String details) {
        Jdbc.run(() -> jdbc.update("INSERT INTO audit_events (actor, action, target, details) VALUES (?, ?, ?, ?)",
                limit(actor, 64), limit(action, 40), limit(target, 120), limit(details, 1000)));
    }

    @Override
    public List<AuditEvent> recent(int limit) {
        return Jdbc.run(() -> jdbc.query("SELECT id, actor, action, target, details, created_at FROM audit_events "
                + "ORDER BY created_at DESC, id DESC LIMIT ?", (rs, n) -> new AuditEvent(rs.getLong("id"), rs.getString("actor"),
                rs.getString("action"), rs.getString("target"), rs.getString("details"), rs.getTimestamp("created_at").toInstant()), limit));
    }
}
