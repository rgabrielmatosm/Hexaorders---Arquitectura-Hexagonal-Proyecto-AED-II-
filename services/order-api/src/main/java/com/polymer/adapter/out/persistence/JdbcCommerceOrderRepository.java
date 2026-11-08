package com.polymer.adapter.out.persistence;

import com.polymer.application.exception.IdempotencyKeyReusedException;
import com.polymer.application.exception.OutOfStockException;
import com.polymer.application.exception.PersistenceUnavailableException;
import com.polymer.application.port.in.AdminDashboard;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.NewShopOrder;
import com.polymer.domain.Order;
import com.polymer.domain.OrderStatus;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.OrderLine;
import com.polymer.domain.fulfillment.StatusChange;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcCommerceOrderRepository implements CommerceOrderRepository {
    private static final String COLUMNS = "o.id, o.customer_id, o.amount, o.currency, o.risk_score, o.status, "
            + "o.fulfillment_status, o.channel, o.version, o.created_at, o.order_number";
    private static final RowMapper<CommerceOrder> HEADER = (rs, n) -> new CommerceOrder(
            rs.getObject("id", UUID.class), rs.getString("customer_id"), rs.getBigDecimal("amount"), rs.getString("currency"),
            rs.getInt("risk_score"), OrderStatus.valueOf(rs.getString("status")),
            FulfillmentStatus.valueOf(rs.getString("fulfillment_status")), rs.getString("channel"), rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(), List.of(), rs.getObject("order_number", Long.class));
    private static final RowMapper<StatusChange> CHANGE = (rs, n) -> new StatusChange(
            rs.getString("from_status") == null ? null : FulfillmentStatus.valueOf(rs.getString("from_status")),
            FulfillmentStatus.valueOf(rs.getString("to_status")), rs.getString("actor"), rs.getString("note"),
            rs.getTimestamp("changed_at").toInstant());

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcCommerceOrderRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    @Override
    public CommerceOrder placeOrder(NewShopOrder shopOrder) {
        Order order = shopOrder.order();
        try {
            return tx.execute(status -> {
                try {
                    jdbc.update("INSERT INTO orders (id, customer_id, amount, currency, risk_score, status, created_at, channel, "
                                    + "fulfillment_status, idempotency_key, user_id) VALUES (?, ?, ?, ?, ?, ?, ?, 'SHOP', ?, ?, ?)",
                            order.id(), order.customerId(), order.amount(), order.currency(), order.riskScore(), order.status().name(),
                            Jdbc.ts(order.createdAt()), shopOrder.status().name(), shopOrder.idempotencyKey(), shopOrder.userId());
                } catch (DuplicateKeyException duplicate) {
                    throw new IdempotencyKeyReusedException(shopOrder.idempotencyKey());
                }
                for (OrderLine line : shopOrder.lines()) {
                    // Conditional decrement: never sells more units than available, even under concurrency.
                    int updated = jdbc.update("UPDATE products SET stock = stock - ?, updated_at = now() "
                            + "WHERE id = ? AND active AND stock >= ?", line.quantity(), line.productId(), line.quantity());
                    if (updated == 0) throw new OutOfStockException(line.productName());
                    jdbc.update("INSERT INTO order_lines (order_id, line_no, product_id, product_name, unit_price, quantity, line_total, "
                                    + "list_unit_price, promotion_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", order.id(), line.lineNo(), line.productId(),
                            line.productName(), line.unitPrice(), line.quantity(), line.lineTotal(), line.listUnitPrice(), line.promotionId());
                }
                shopOrder.history().forEach(change -> insertChange(order.id(), change));
                var shipping = shopOrder.shipping();
                if (shipping != null) {
                    jdbc.update("INSERT INTO order_shipping (order_id, recipient, address, city, phone, instructions) VALUES (?, ?, ?, ?, ?, ?)",
                            order.id(), shipping.recipient(), shipping.address(), shipping.city(), shipping.phone(), shipping.instructions());
                }
                var checkout = shopOrder.checkout();
                if (checkout != null) {
                    jdbc.update("INSERT INTO order_checkout (order_id, delivery_method, payment_method, subtotal, savings, shipping_cost, total) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, ?)", order.id(), checkout.delivery().name(),
                            checkout.payment() == null ? null : checkout.payment().name(), checkout.subtotal(), checkout.savings(),
                            checkout.shippingCost(), checkout.total());
                }
                return findById(order.id()).orElseThrow();
            });
        } catch (OutOfStockException | IdempotencyKeyReusedException business) {
            throw business;
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    private void insertChange(UUID orderId, StatusChange change) {
        jdbc.update("INSERT INTO order_status_history (order_id, from_status, to_status, actor, note, changed_at) VALUES (?, ?, ?, ?, ?, ?)",
                orderId, change.from() == null ? null : change.from().name(), change.to().name(), change.actor(), change.note(),
                Jdbc.ts(change.at()));
    }

    @Override
    public Optional<com.polymer.domain.fulfillment.ShippingAddress> shippingOf(UUID orderId) {
        return Jdbc.run(() -> jdbc.query("SELECT recipient, address, city, phone, instructions FROM order_shipping WHERE order_id = ?",
                (rs, n) -> new com.polymer.domain.fulfillment.ShippingAddress(rs.getString("recipient"), rs.getString("address"),
                        rs.getString("city"), rs.getString("phone"), rs.getString("instructions")), orderId).stream().findFirst());
    }

    @Override
    public Optional<com.polymer.domain.fulfillment.OrderCheckout> checkoutOf(UUID orderId) {
        return Jdbc.run(() -> jdbc.query("SELECT delivery_method, payment_method, subtotal, savings, shipping_cost, total FROM order_checkout "
                + "WHERE order_id = ?", (rs, n) -> new com.polymer.domain.fulfillment.OrderCheckout(
                com.polymer.domain.fulfillment.DeliveryMethod.valueOf(rs.getString("delivery_method")),
                rs.getString("payment_method") == null ? null : com.polymer.domain.fulfillment.PaymentMethod.valueOf(rs.getString("payment_method")),
                rs.getBigDecimal("subtotal"), rs.getBigDecimal("savings"), rs.getBigDecimal("shipping_cost"), rs.getBigDecimal("total")),
                orderId).stream().findFirst());
    }

    @Override
    public Optional<CommerceOrder> findById(UUID id) {
        return Jdbc.run(() -> withLines(jdbc.query("SELECT " + COLUMNS + " FROM orders o WHERE o.id = ?", HEADER, id))).stream().findFirst();
    }

    @Override
    public Optional<CommerceOrder> findByIdempotencyKey(String key) {
        return Jdbc.run(() -> withLines(jdbc.query("SELECT " + COLUMNS + " FROM orders o WHERE o.idempotency_key = ?", HEADER, key)))
                .stream().findFirst();
    }

    @Override
    public Optional<Long> ownerOf(UUID orderId) {
        return Jdbc.run(() -> jdbc.query("SELECT user_id FROM orders WHERE id = ? AND user_id IS NOT NULL",
                (rs, n) -> rs.getLong(1), orderId).stream().findFirst());
    }

    @Override
    public List<CommerceOrder> byCustomer(long userId) {
        return Jdbc.run(() -> withLines(jdbc.query("SELECT " + COLUMNS + " FROM orders o WHERE o.user_id = ? "
                + "ORDER BY o.created_at DESC LIMIT 200", HEADER, userId)));
    }

    @Override
    public List<CommerceOrder> byStatuses(Set<FulfillmentStatus> statuses, int limit) {
        if (statuses.isEmpty()) return List.of();
        String placeholders = String.join(",", statuses.stream().map(s -> "?").toList());
        List<Object> args = new ArrayList<>(statuses.stream().map(Enum::name).toList());
        args.add(limit);
        return Jdbc.run(() -> withLines(jdbc.query("SELECT " + COLUMNS + " FROM orders o WHERE o.fulfillment_status IN ("
                + placeholders + ") ORDER BY o.created_at DESC LIMIT ?", HEADER, args.toArray())));
    }

    /** Loads all lines of the given orders with one query. */
    private List<CommerceOrder> withLines(List<CommerceOrder> headers) {
        if (headers.isEmpty()) return headers;
        String placeholders = String.join(",", headers.stream().map(h -> "?").toList());
        Map<UUID, List<OrderLine>> lines = new HashMap<>();
        jdbc.query("SELECT order_id, line_no, product_id, product_name, unit_price, quantity, line_total, list_unit_price, promotion_id "
                + "FROM order_lines WHERE order_id IN (" + placeholders + ") ORDER BY order_id, line_no", rs -> {
                    lines.computeIfAbsent(rs.getObject("order_id", UUID.class), k -> new ArrayList<>()).add(new OrderLine(
                            rs.getInt("line_no"), rs.getLong("product_id"), rs.getString("product_name"),
                            rs.getBigDecimal("unit_price"), rs.getInt("quantity"), rs.getBigDecimal("line_total"),
                            rs.getBigDecimal("list_unit_price"), rs.getObject("promotion_id", Long.class)));
                }, headers.stream().map(CommerceOrder::id).toArray());
        return headers.stream().map(h -> new CommerceOrder(h.id(), h.customerId(), h.amount(), h.currency(), h.riskScore(),
                h.riskDecision(), h.status(), h.channel(), h.version(), h.createdAt(), lines.getOrDefault(h.id(), List.of()),
                h.orderNumber())).toList();
    }

    @Override
    public List<StatusChange> history(UUID orderId) {
        return Jdbc.run(() -> jdbc.query("SELECT from_status, to_status, actor, note, changed_at FROM order_status_history "
                + "WHERE order_id = ? ORDER BY id", CHANGE, orderId));
    }

    @Override
    public boolean transition(UUID orderId, int expectedVersion, FulfillmentStatus expectedStatus, StatusChange change, boolean releaseStock) {
        try {
            return Boolean.TRUE.equals(tx.execute(status -> {
                int rows = jdbc.update("UPDATE orders SET fulfillment_status = ?, version = version + 1 "
                        + "WHERE id = ? AND version = ? AND fulfillment_status = ?", change.to().name(), orderId, expectedVersion, expectedStatus.name());
                if (rows == 0) return false; // concurrent modification: nothing written
                if (releaseStock) {
                    jdbc.update("UPDATE products p SET stock = p.stock + l.quantity, updated_at = now() FROM order_lines l "
                            + "WHERE l.order_id = ? AND p.id = l.product_id", orderId);
                }
                insertChange(orderId, change);
                return true;
            }));
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    @Override
    public Map<String, Long> countByStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (FulfillmentStatus status : FulfillmentStatus.values()) counts.put(status.name(), 0L);
        Jdbc.run(() -> {
            jdbc.query("SELECT fulfillment_status, count(*) FROM orders GROUP BY fulfillment_status",
                    rs -> { counts.put(rs.getString(1), rs.getLong(2)); });
            return null;
        });
        return counts;
    }

    @Override
    public long countAll() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT count(*) FROM orders", Long.class));
    }

    @Override
    public BigDecimal shopSales() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM orders WHERE channel = 'SHOP' AND currency = 'PEN' "
                + "AND fulfillment_status NOT IN ('CANCELLED', 'REJECTED')", BigDecimal.class)).setScale(2);
    }

    @Override
    public BigDecimal legacyUsdSales() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM orders WHERE channel = 'SHOP' AND currency = 'USD' "
                + "AND fulfillment_status NOT IN ('CANCELLED', 'REJECTED')", BigDecimal.class)).setScale(2);
    }

    @Override
    public List<AdminDashboard.TopProduct> topProducts(int limit) {
        return Jdbc.run(() -> jdbc.query("SELECT l.product_id, max(l.product_name) AS name, sum(l.quantity) AS units, sum(l.line_total) AS revenue, "
                + "o.currency FROM order_lines l JOIN orders o ON o.id = l.order_id WHERE o.fulfillment_status NOT IN ('CANCELLED', 'REJECTED') "
                + "GROUP BY l.product_id, o.currency ORDER BY units DESC, revenue DESC LIMIT ?",
                (rs, n) -> new AdminDashboard.TopProduct(rs.getLong("product_id"), rs.getString("name"), rs.getLong("units"),
                        rs.getBigDecimal("revenue").setScale(2), rs.getString("currency")), limit));
    }

    @Override
    public Map<Long, Long> unitsSold() {
        Map<Long, Long> units = new HashMap<>();
        Jdbc.run(() -> {
            jdbc.query("SELECT l.product_id, sum(l.quantity) FROM order_lines l JOIN orders o ON o.id = l.order_id "
                    + "WHERE o.fulfillment_status NOT IN ('CANCELLED', 'REJECTED') GROUP BY l.product_id",
                    rs -> { units.put(rs.getLong(1), rs.getLong(2)); });
            return null;
        });
        return units;
    }

    @Override
    public long countShopOrders() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT count(*) FROM orders WHERE channel = 'SHOP'", Long.class));
    }

    @Override
    public Map<LocalDate, Long> ordersPerDay(Instant since) {
        LocalDate first = since.atZone(ZoneOffset.UTC).toLocalDate();
        Map<LocalDate, Long> days = new TreeMap<>();
        for (LocalDate day = first; !day.isAfter(LocalDate.now(ZoneOffset.UTC)); day = day.plusDays(1)) days.put(day, 0L);
        Jdbc.run(() -> {
            jdbc.query("SELECT (created_at AT TIME ZONE 'UTC')::date AS day, count(*) FROM orders WHERE created_at >= ? "
                    + "GROUP BY day", rs -> { days.put(rs.getDate(1).toLocalDate(), rs.getLong(2)); }, Jdbc.ts(first.atStartOfDay().toInstant(ZoneOffset.UTC)));
            return null;
        });
        return days;
    }

    @Override
    public List<AdminDashboard.SalesByCategory> salesByCategory() {
        // v6: grouped by MAIN category and currency (orders before v6 were in USD).
        return Jdbc.run(() -> jdbc.query("SELECT COALESCE(m.name, c.name) AS category, sum(l.quantity) AS units, sum(l.line_total) AS revenue, "
                + "o.currency FROM order_lines l JOIN orders o ON o.id = l.order_id JOIN products p ON p.id = l.product_id "
                + "JOIN categories c ON c.id = p.category_id LEFT JOIN categories m ON m.id = c.parent_id "
                + "WHERE o.fulfillment_status NOT IN ('CANCELLED', 'REJECTED') GROUP BY 1, o.currency ORDER BY o.currency DESC, revenue DESC",
                (rs, n) -> new AdminDashboard.SalesByCategory(rs.getString(1), rs.getLong(2), rs.getBigDecimal(3).setScale(2), rs.getString(4))));
    }
}
