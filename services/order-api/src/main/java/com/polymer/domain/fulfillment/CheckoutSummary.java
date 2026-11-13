package com.polymer.domain.fulfillment;

import java.math.BigDecimal;
import java.util.Objects;

/** Totals of a checkout: products (already discounted) + shipping. Payment may be null in a preview. */
public record CheckoutSummary(Quote quote, DeliveryMethod delivery, PaymentMethod payment, BigDecimal subtotal,
                              BigDecimal savings, BigDecimal shippingCost, BigDecimal total) {
    public CheckoutSummary {
        Objects.requireNonNull(quote, "quote");
        Objects.requireNonNull(delivery, "delivery");
        if (payment != null && !payment.allowedWith(delivery)) {
            throw new IllegalArgumentException("paymentMethod: " + payment.label() + " no está disponible con " + delivery.label());
        }
        if (total.compareTo(subtotal.add(shippingCost)) != 0) throw new IllegalArgumentException("El total no coincide");
        if (total.compareTo(Quote.MAX_TOTAL) > 0) throw new IllegalArgumentException("El total supera 1000000.00");
    }

    public static CheckoutSummary of(Quote quote, DeliveryMethod delivery, PaymentMethod payment) {
        DeliveryMethod method = delivery == null ? DeliveryMethod.STANDARD : delivery;
        BigDecimal shipping = method.costFor(quote.total());
        return new CheckoutSummary(quote, method, payment, quote.total(), quote.savings(), shipping, quote.total().add(shipping));
    }
}
