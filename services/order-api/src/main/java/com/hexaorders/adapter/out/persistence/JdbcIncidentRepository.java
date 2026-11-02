package com.hexaorders.adapter.out.persistence;

import com.hexaorders.application.port.out.IncidentRepository;
import com.hexaorders.domain.incident.Incident;
import com.hexaorders.domain.incident.IncidentCategory;
import com.hexaorders.domain.incident.IncidentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcIncidentRepository implements IncidentRepository {
    private static final String COLUMNS = "id, order_id, category, description, status, reported_by, created_at, resolved_at, resolution";
    private static final RowMapper<Incident> INCIDENT = (rs, n) -> new Incident(rs.getLong("id"), rs.getObject("order_id", UUID.class),
            IncidentCategory.valueOf(rs.getString("category")), rs.getString("description"), IncidentStatus.valueOf(rs.getString("status")),
            rs.getString("reported_by"), rs.getTimestamp("created_at").toInstant(), Jdbc.instant(rs.getTimestamp("resolved_at")),
            rs.getString("resolution"));

    private final JdbcTemplate jdbc;

    public JdbcIncidentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Incident create(UUID orderId, IncidentCategory category, String description, String reportedBy, Instant at) {
        return Jdbc.run(() -> jdbc.queryForObject("INSERT INTO incidents (order_id, category, description, reported_by, created_at) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING " + COLUMNS, INCIDENT, orderId, category.name(), description, reportedBy, Jdbc.ts(at)));
    }

    @Override
    public List<Incident> list(IncidentStatus status) {
        if (status == null) {
            return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM incidents ORDER BY created_at DESC LIMIT 300", INCIDENT));
        }
        return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM incidents WHERE status = ? ORDER BY created_at DESC LIMIT 300",
                INCIDENT, status.name()));
    }

    @Override
    public Optional<Incident> resolve(long id, String resolution, Instant at) {
        return Jdbc.run(() -> jdbc.query("UPDATE incidents SET status = 'RESOLVED', resolution = ?, resolved_at = ? "
                + "WHERE id = ? AND status = 'OPEN' RETURNING " + COLUMNS, INCIDENT, resolution, Jdbc.ts(at), id).stream().findFirst());
    }

    @Override
    public long countOpen() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT count(*) FROM incidents WHERE status = 'OPEN'", Long.class));
    }
}
