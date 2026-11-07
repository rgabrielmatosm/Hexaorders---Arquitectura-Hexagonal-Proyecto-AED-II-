package com.hexaorders.domain.fulfillment;

import java.math.BigDecimal;

/** Delivery, payment and totals recorded with a v6 store order (read model). */
public record OrderCheckout(DeliveryMethod delivery, PaymentMethod payment, BigDecimal subtotal, BigDecimal savings,
                            BigDecimal shippingCost, BigDecimal total) {
    public static OrderCheckout of(CheckoutSummary summary) {
        return new OrderCheckout(summary.delivery(), summary.payment(), summary.subtotal(), summary.savings(), summary.shippingCost(), summary.total());
    }
}
