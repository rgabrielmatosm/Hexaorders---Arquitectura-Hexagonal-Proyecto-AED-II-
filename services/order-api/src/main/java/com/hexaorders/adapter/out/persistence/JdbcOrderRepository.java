package com.hexaorders.adapter.out.persistence;

import com.hexaorders.application.exception.PersistenceUnavailableException;
import com.hexaorders.application.port.out.OrderRepository;
import com.hexaorders.domain.Order;
import com.hexaorders.domain.OrderStatistics;
import com.hexaorders.domain.OrderStatus;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcOrderRepository implements OrderRepository {
    private static final String COLUMNS = "id, customer_id, amount, currency, risk_score, status, created_at";
    private static final RowMapper<Order> ROW_MAPPER = (rs, rowNumber) -> new Order(
            rs.getObject("id", UUID.class), rs.getString("customer_id"), rs.getBigDecimal("amount"),
            rs.getString("currency"), rs.getInt("risk_score"), OrderStatus.valueOf(rs.getString("status")),
            rs.getTimestamp("created_at").toInstant());
    private final JdbcTemplate jdbc;

    public JdbcOrderRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void save(Order order) {
        try {
            // One statement is atomic in PostgreSQL autocommit; there is no transaction around HTTP.
            // Orders of the original API are channel LAB; their lifecycle starts at the risk decision (V2).
            jdbc.update("INSERT INTO orders (" + COLUMNS + ", channel, fulfillment_status) VALUES (?, ?, ?, ?, ?, ?, ?, 'LAB', ?)",
                    order.id(), order.customerId(), order.amount(), order.currency(), order.riskScore(),
                    order.status().name(), Timestamp.from(order.createdAt()),
                    FulfillmentStatus.fromRiskDecision(order.status()).name());
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    @Override
    public Optional<Order> findById(UUID id) {
        try {
            // The original public API only exposes LAB orders: portal (SHOP) orders belong to customers.
            return jdbc.query("SELECT " + COLUMNS + " FROM orders WHERE id = ? AND channel = 'LAB'", ROW_MAPPER, id)
                    .stream().findFirst();
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    @Override
    public OrderStatistics statistics() {
        try {
            // One aggregate query over the whole table; status values are enforced by a CHECK constraint.
            return jdbc.queryForObject("SELECT count(*) FILTER (WHERE status = 'APPROVED') AS approved, "
                            + "count(*) FILTER (WHERE status = 'REVIEW') AS review FROM orders",
                    (rs, rowNumber) -> OrderStatistics.of(rs.getLong("approved"), rs.getLong("review")));
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    @Override
    public List<Order> latest(int limit) {
        try {
            return jdbc.query("SELECT " + COLUMNS + " FROM orders WHERE channel = 'LAB' ORDER BY created_at DESC, id DESC LIMIT ?",
                    ROW_MAPPER, limit);
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }
}
