package com.polymer.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Order(UUID id, String customerId, BigDecimal amount, String currency,
                    int riskScore, OrderStatus status, Instant createdAt) {
    public Order {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(createdAt, "createdAt");
        OrderDraft validated = new OrderDraft(customerId, amount, currency);
        amount = validated.amount();
        if (status != OrderStatus.fromRiskScore(riskScore)) {
            throw new IllegalArgumentException("El estado no corresponde al puntaje de riesgo");
        }
    }

    public static Order create(OrderDraft draft, int riskScore, UUID id, Instant createdAt) {
        return new Order(id, draft.customerId(), draft.amount(), draft.currency(),
                riskScore, OrderStatus.fromRiskScore(riskScore), createdAt);
    }
}
