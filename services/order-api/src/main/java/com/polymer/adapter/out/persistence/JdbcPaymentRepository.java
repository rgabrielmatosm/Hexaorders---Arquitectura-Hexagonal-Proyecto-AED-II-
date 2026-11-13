package com.polymer.adapter.out.persistence;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.port.out.PaymentRepository;
import com.polymer.domain.fulfillment.PaymentMethod;
import com.polymer.domain.payment.Payment;
import com.polymer.domain.payment.PaymentStatus;
import com.polymer.domain.payment.Receipt;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcPaymentRepository implements PaymentRepository {
    private static final String COLUMNS = "id, order_id, method, amount, currency, status, created_at, expires_at, paid_at, reference, "
            + "card_brand, card_last4, failure_code, receipt_series, receipt_number, receipt_base, receipt_igv";
    private final JdbcTemplate jdbc;

    public JdbcPaymentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void save(Payment p) {
        Receipt r = p.receipt();
        try {
            Jdbc.run(() -> jdbc.update("INSERT INTO payments (" + COLUMNS + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                    + "ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, paid_at = EXCLUDED.paid_at, reference = EXCLUDED.reference, "
                    + "failure_code = EXCLUDED.failure_code, receipt_series = EXCLUDED.receipt_series, receipt_number = EXCLUDED.receipt_number, "
                    + "receipt_base = EXCLUDED.receipt_base, receipt_igv = EXCLUDED.receipt_igv",
                    p.id(), p.orderId(), p.method().name(), p.amount(), p.currency(), p.status().name(), Jdbc.ts(p.createdAt()),
                    Jdbc.ts(p.expiresAt()), Jdbc.ts(p.paidAt()), p.reference(), p.cardBrand(), p.cardLast4(), p.failureCode(),
                    r == null ? null : r.series(), r == null ? null : r.number(), r == null ? null : r.taxableBase(), r == null ? null : r.igv()));
        } catch (DuplicateKeyException alreadyPaid) {
            throw new BusinessConflictException("ALREADY_PAID", "El pedido ya está pagado");
        }
    }

    @Override
    public Optional<Payment> find(UUID paymentId) {
        return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM payments WHERE id = ?", (rs, n) -> map(rs), paymentId)).stream().findFirst();
    }

    @Override
    public List<Payment> forOrder(UUID orderId) {
        return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM payments WHERE order_id = ? ORDER BY created_at, (status = 'PENDING')", (rs, n) -> map(rs), orderId));
    }

    @Override
    public long nextReceiptNumber() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT nextval('receipt_number_seq')", Long.class));
    }

    @Override
    public void recordMethodUsed(UUID orderId, PaymentMethod method) {
        Jdbc.run(() -> jdbc.update("UPDATE order_checkout SET payment_method = ? WHERE order_id = ?", method.name(), orderId));
    }

    private static Payment map(ResultSet rs) throws SQLException {
        long number = rs.getLong("receipt_number");
        Receipt receipt = rs.wasNull() ? null : new Receipt(rs.getString("receipt_series"), number, rs.getBigDecimal("receipt_base"),
                rs.getBigDecimal("receipt_igv"), rs.getBigDecimal("amount"));
        return new Payment(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), PaymentMethod.valueOf(rs.getString("method")),
                rs.getBigDecimal("amount"), rs.getString("currency"), PaymentStatus.valueOf(rs.getString("status")),
                Jdbc.instant(rs.getTimestamp("created_at")), Jdbc.instant(rs.getTimestamp("expires_at")), Jdbc.instant(rs.getTimestamp("paid_at")),
                rs.getString("reference"), rs.getString("card_brand"), rs.getString("card_last4"), rs.getString("failure_code"), receipt);
    }
}
