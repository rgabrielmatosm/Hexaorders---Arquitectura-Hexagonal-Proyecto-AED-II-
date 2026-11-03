package com.hexaorders.domain.fulfillment;

import com.hexaorders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Read model of an order with its lifecycle. channel: LAB (original API) or SHOP (portal).
 * v6: orderNumber is the customer-facing number (shown as HX-100001).
 */
public record CommerceOrder(UUID id, String customerId, BigDecimal amount, String currency, int riskScore,
                            OrderStatus riskDecision, FulfillmentStatus status, String channel, int version,
                            Instant createdAt, List<OrderLine> lines, Long orderNumber) {
    public CommerceOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** v2–v5 constructor. */
    public CommerceOrder(UUID id, String customerId, BigDecimal amount, String currency, int riskScore, OrderStatus riskDecision,
                         FulfillmentStatus status, String channel, int version, Instant createdAt, List<OrderLine> lines) {
        this(id, customerId, amount, currency, riskScore, riskDecision, status, channel, version, createdAt, lines, null);
    }

    /** Customer-facing order number, e.g. "HX-100001" (null for orders without one). */
    public String reference() { return orderNumber == null ? null : "HX-" + orderNumber; }
}
