package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.EventOutbox;
import com.polymer.domain.event.BusinessEvent;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Outbox over the history tables that are written in the same transaction as each business change
 * (order_status_history and delivery_changes). Rows older than the V5 watermark are never sent,
 * so upgrading does not e-mail customers about past orders. A row is "pending" until its eventId
 * is stored in outbox_published; ordering gaps from concurrent commits are therefore harmless.
 */
public final class JdbcEventOutbox implements EventOutbox {
    private static final String PENDING_STATUS = "FROM order_status_history h JOIN orders o ON o.id = h.order_id "
            + "WHERE o.channel = 'SHOP' AND h.id > (SELECT order_history_after FROM event_relay_watermark) "
            + "AND NOT EXISTS (SELECT 1 FROM outbox_published p WHERE p.event_id = 'order-status-' || h.id) ";
    private static final String PENDING_DELIVERY = "FROM delivery_changes c JOIN deliveries d ON d.id = c.delivery_id "
            + "WHERE c.id > (SELECT delivery_changes_after FROM event_relay_watermark) "
            + "AND NOT EXISTS (SELECT 1 FROM outbox_published p WHERE p.event_id = 'delivery-change-' || c.id) ";
    // v7: pagos aprobados (tabla nueva: todas sus filas son posteriores a la migración, no necesitan marca de agua).
    private static final String PENDING_PAYMENT = "FROM payments p WHERE p.status = 'PAID' "
            + "AND NOT EXISTS (SELECT 1 FROM outbox_published x WHERE x.event_id = 'payment-' || p.id) ";
    private final JdbcTemplate jdbc;

    public JdbcEventOutbox(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<BusinessEvent> pending(int limit) {
        List<BusinessEvent> events = new ArrayList<>(Jdbc.run(() -> jdbc.query(
                "SELECT h.id, h.order_id, h.from_status, h.to_status, h.changed_at " + PENDING_STATUS + "ORDER BY h.id LIMIT ?",
                (rs, n) -> BusinessEvent.fromStatusChange(rs.getLong("id"), rs.getObject("order_id", UUID.class),
                        rs.getString("from_status") == null ? null : FulfillmentStatus.valueOf(rs.getString("from_status")),
                        FulfillmentStatus.valueOf(rs.getString("to_status")), Jdbc.instant(rs.getTimestamp("changed_at"))), limit)));
        events.addAll(Jdbc.run(() -> jdbc.query(
                "SELECT c.id, d.order_id, c.action, c.new_start, c.new_end, c.changed_at " + PENDING_DELIVERY + "ORDER BY c.id LIMIT ?",
                (rs, n) -> BusinessEvent.fromDeliveryChange(rs.getLong("id"), rs.getObject("order_id", UUID.class), rs.getString("action"),
                        Jdbc.instant(rs.getTimestamp("new_start")), Jdbc.instant(rs.getTimestamp("new_end")),
                        Jdbc.instant(rs.getTimestamp("changed_at"))), limit)));
        events.addAll(Jdbc.run(() -> jdbc.query(
                "SELECT p.id, p.order_id, p.method, p.receipt_series, p.receipt_number, p.paid_at " + PENDING_PAYMENT + "ORDER BY p.paid_at LIMIT ?",
                (rs, n) -> BusinessEvent.fromPayment(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getString("method"),
                        rs.getString("receipt_series") + "-" + String.format("%08d", rs.getLong("receipt_number")),
                        Jdbc.instant(rs.getTimestamp("paid_at"))), limit)));
        events.sort(Comparator.comparing(BusinessEvent::occurredAt).thenComparing(BusinessEvent::eventId));
        return events.size() > limit ? List.copyOf(events.subList(0, limit)) : events;
    }

    @Override
    public void markPublished(String eventId) {
        Jdbc.run(() -> jdbc.update("INSERT INTO outbox_published (event_id, published_at) VALUES (?, now()) ON CONFLICT DO NOTHING", eventId));
    }

    @Override
    public long pendingCount() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT (SELECT count(*) " + PENDING_STATUS + ") + (SELECT count(*) " + PENDING_DELIVERY + ") + (SELECT count(*) " + PENDING_PAYMENT + ")", Long.class));
    }
}
