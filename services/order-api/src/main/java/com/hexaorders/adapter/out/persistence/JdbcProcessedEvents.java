package com.hexaorders.adapter.out.persistence;

import com.hexaorders.application.port.out.ProcessedEvents;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcProcessedEvents implements ProcessedEvents {
    private final JdbcTemplate jdbc;

    public JdbcProcessedEvents(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public boolean alreadyProcessed(String eventId, String consumer) {
        return Boolean.TRUE.equals(Jdbc.run(() -> jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM processed_events WHERE event_id = ? AND consumer = ?)", Boolean.class, eventId, consumer)));
    }

    @Override
    public void markProcessed(String eventId, String consumer) {
        Jdbc.run(() -> jdbc.update("INSERT INTO processed_events (event_id, consumer, processed_at) VALUES (?, ?, now()) "
                + "ON CONFLICT DO NOTHING", eventId, consumer));
    }
}
