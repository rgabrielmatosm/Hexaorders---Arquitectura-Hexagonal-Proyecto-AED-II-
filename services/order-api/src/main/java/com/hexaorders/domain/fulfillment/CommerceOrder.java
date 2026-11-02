package com.hexaorders.domain.fulfillment;

import com.hexaorders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Read model of an order with its lifecycle. channel: LAB (original API) or SHOP (portal). */
public record CommerceOrder(UUID id, String customerId, BigDecimal amount, String currency, int riskScore,
                            OrderStatus riskDecision, FulfillmentStatus status, String channel, int version,
                            Instant createdAt, List<OrderLine> lines) {
    public CommerceOrder {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
