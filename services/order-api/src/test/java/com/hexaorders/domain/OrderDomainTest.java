package com.hexaorders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class OrderDomainTest {
    @Test
    void riskThresholdIsOwnedByDomain() {
        assertThat(OrderStatus.fromRiskScore(0)).isEqualTo(OrderStatus.APPROVED);
        assertThat(OrderStatus.fromRiskScore(69)).isEqualTo(OrderStatus.APPROVED);
        assertThat(OrderStatus.fromRiskScore(70)).isEqualTo(OrderStatus.REVIEW);
        assertThat(OrderStatus.fromRiskScore(100)).isEqualTo(OrderStatus.REVIEW);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101})
    void rejectsInvalidRiskScore(int score) {
        assertThatThrownBy(() -> OrderStatus.fromRiskScore(score)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01", "1000000.01", "1.001", "1.000"})
    void rejectsInvalidAmountsWithoutRounding(String amount) {
        assertThatThrownBy(() -> new OrderDraft("customer_1", new BigDecimal(amount), "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void preservesDecimalMoneyAndUpperBoundary() {
        assertThat(new OrderDraft("customer_1", new BigDecimal("0.10"), "USD").amount())
                .isEqualByComparingTo("0.10");
        assertThat(new OrderDraft("customer_1", new BigDecimal("1000000"), "USD").amount())
                .isEqualTo(new BigDecimal("1000000.00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "a b", "a.b", "<script>", "cliente/1", "ñ"})
    void rejectsUnsafeCustomerIdentifiers(String customerId) {
        assertThatThrownBy(() -> new OrderDraft(customerId, BigDecimal.ONE, "USD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedCurrencyAndContradictoryStatus() {
        assertThatThrownBy(() -> new OrderDraft("demo", BigDecimal.ONE, "PEN"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Order(UUID.randomUUID(), "demo", BigDecimal.ONE, "USD", 80,
                OrderStatus.APPROVED, Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
