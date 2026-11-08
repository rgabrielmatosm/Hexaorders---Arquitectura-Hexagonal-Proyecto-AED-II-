package com.polymer.adapter.out.persistence;

import com.polymer.application.exception.PersistenceUnavailableException;
import com.polymer.application.port.in.DeliveryView;
import com.polymer.application.port.out.DeliveryRepository;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.logistics.Delivery;
import com.polymer.domain.logistics.DeliveryChange;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcDeliveryRepository implements DeliveryRepository {
    private static final String SELECT = "SELECT d.id, d.order_id, d.assignee_id, u.display_name AS assignee_name, d.window_start, "
            + "d.window_end, d.notes, d.version FROM deliveries d JOIN app_users u ON u.id = d.assignee_id ";
    private static final RowMapper<Delivery> DELIVERY = (rs, n) -> new Delivery(rs.getLong("id"), rs.getObject("order_id", UUID.class),
            rs.getLong("assignee_id"), rs.getString("assignee_name"), rs.getTimestamp("window_start").toInstant(),
            rs.getTimestamp("window_end").toInstant(), rs.getString("notes"), rs.getInt("version"));

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcDeliveryRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public Optional<Delivery> findById(long id) {
        return Jdbc.run(() -> jdbc.query(SELECT + "WHERE d.id = ?", DELIVERY, id).stream().findFirst());
    }

    @Override
    public Optional<Delivery> findByOrder(UUID orderId) {
        return Jdbc.run(() -> jdbc.query(SELECT + "WHERE d.order_id = ?", DELIVERY, orderId).stream().findFirst());
    }

    @Override
    public List<DeliveryView> inRange(Instant from, Instant to, Long assigneeId) {
        List<Object> args = new ArrayList<>(List.of(Jdbc.ts(to), Jdbc.ts(from)));
        String filter = "";
        if (assigneeId != null) {
            filter = "AND d.assignee_id = ? ";
            args.add(assigneeId);
        }
        String sql = "SELECT d.id, d.order_id, d.assignee_id, u.display_name AS assignee_name, d.window_start, d.window_end, d.notes, "
                + "d.version, o.fulfillment_status, o.customer_id, o.amount, s.city FROM deliveries d JOIN app_users u ON u.id = d.assignee_id "
                + "JOIN orders o ON o.id = d.order_id LEFT JOIN order_shipping s ON s.order_id = d.order_id WHERE d.window_start < ? AND d.window_end > ? " + filter + "ORDER BY d.window_start";
        return Jdbc.run(() -> jdbc.query(sql, (rs, n) -> new DeliveryView(DELIVERY.mapRow(rs, n),
                FulfillmentStatus.valueOf(rs.getString("fulfillment_status")), rs.getString("customer_id"), rs.getBigDecimal("amount"),
                rs.getString("city")),
                args.toArray()));
    }

    @Override
    public List<Delivery> overlapping(long assigneeId, Instant start, Instant end, Long excludingId) {
        return Jdbc.run(() -> jdbc.query(SELECT + "WHERE d.assignee_id = ? AND d.window_start < ? AND d.window_end > ? "
                + "AND (?::bigint IS NULL OR d.id <> ?::bigint) ORDER BY d.window_start",
                DELIVERY, assigneeId, Jdbc.ts(end), Jdbc.ts(start), excludingId, excludingId));
    }

    @Override
    public Delivery create(Delivery delivery, DeliveryChange change) {
        try {
            return tx.execute(status -> {
                Long id = jdbc.queryForObject("INSERT INTO deliveries (order_id, assignee_id, window_start, window_end, notes) "
                        + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, delivery.orderId(), delivery.assigneeId(),
                        Jdbc.ts(delivery.start()), Jdbc.ts(delivery.end()), delivery.notes());
                insertChange(id, change);
                return findById(id).orElseThrow();
            });
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    @Override
    public Optional<Delivery> reschedule(long id, int expectedVersion, long assigneeId, Instant start, Instant end, DeliveryChange change) {
        try {
            return tx.execute(status -> {
                int rows = jdbc.update("UPDATE deliveries SET assignee_id = ?, window_start = ?, window_end = ?, version = version + 1 "
                        + "WHERE id = ? AND version = ?", assigneeId, Jdbc.ts(start), Jdbc.ts(end), id, expectedVersion);
                if (rows == 0) return Optional.<Delivery>empty();
                insertChange(id, change);
                return findById(id);
            });
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    private void insertChange(long deliveryId, DeliveryChange c) {
        jdbc.update("INSERT INTO delivery_changes (delivery_id, actor, action, old_start, old_end, old_assignee, new_start, new_end, "
                        + "new_assignee, reason, changed_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", deliveryId, c.actor(), c.action(),
                Jdbc.ts(c.oldStart()), Jdbc.ts(c.oldEnd()), c.oldAssignee(), Jdbc.ts(c.newStart()), Jdbc.ts(c.newEnd()),
                c.newAssignee(), c.reason(), Jdbc.ts(c.at()));
    }

    @Override
    public List<DeliveryChange> history(long deliveryId) {
        return Jdbc.run(() -> jdbc.query("SELECT * FROM delivery_changes WHERE delivery_id = ? ORDER BY id", (rs, n) -> new DeliveryChange(
                rs.getLong("delivery_id"), rs.getString("actor"), rs.getString("action"), Jdbc.instant(rs.getTimestamp("old_start")),
                Jdbc.instant(rs.getTimestamp("old_end")), rs.getString("old_assignee"), Jdbc.instant(rs.getTimestamp("new_start")),
                Jdbc.instant(rs.getTimestamp("new_end")), rs.getString("new_assignee"), rs.getString("reason"),
                Jdbc.instant(rs.getTimestamp("changed_at"))), deliveryId));
    }
}
