package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.ReceiptSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcReceiptSource implements ReceiptSource {
    private final JdbcTemplate jdbc;

    public JdbcReceiptSource(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<ReceiptMail> forOrder(UUID orderId) {
        List<Line> lines = Jdbc.run(() -> jdbc.query("SELECT product_name, quantity, unit_price, line_total FROM order_lines WHERE order_id = ? ORDER BY line_no",
                (rs, n) -> new Line(rs.getString(1), rs.getInt(2), rs.getBigDecimal(3), rs.getBigDecimal(4)), orderId));
        return Jdbc.run(() -> jdbc.query("SELECT p.receipt_series, p.receipt_number, p.paid_at, p.method, p.reference, p.card_brand, p.card_last4, "
                + "p.amount, p.currency, p.receipt_base, p.receipt_igv, c.subtotal, c.savings, c.shipping_cost, c.delivery_method "
                + "FROM payments p LEFT JOIN order_checkout c ON c.order_id = p.order_id WHERE p.order_id = ? AND p.status = 'PAID'",
                (rs, n) -> {
                    BigDecimal total = rs.getBigDecimal("amount");
                    return new ReceiptMail(rs.getString("receipt_series") + "-" + String.format("%08d", rs.getLong("receipt_number")),
                            Jdbc.instant(rs.getTimestamp("paid_at")), rs.getString("method"), rs.getString("reference"), rs.getString("card_brand"),
                            rs.getString("card_last4"), lines, rs.getBigDecimal("subtotal") == null ? total : rs.getBigDecimal("subtotal"),
                            rs.getBigDecimal("savings") == null ? BigDecimal.ZERO : rs.getBigDecimal("savings"),
                            rs.getBigDecimal("shipping_cost") == null ? BigDecimal.ZERO : rs.getBigDecimal("shipping_cost"),
                            rs.getBigDecimal("receipt_base"), rs.getBigDecimal("receipt_igv"), total, rs.getString("currency"),
                            rs.getString("delivery_method"));
                }, orderId)).stream().findFirst();
    }
}
