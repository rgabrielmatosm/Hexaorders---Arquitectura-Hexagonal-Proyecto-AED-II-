package com.hexaorders.domain.fulfillment;

import java.math.BigDecimal;
import java.util.Objects;

/** Line of a shop order. Name and price are snapshots taken when the order is placed. */
public record OrderLine(int lineNo, long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    public OrderLine {
        Objects.requireNonNull(productName, "productName");
        if (quantity < 1) throw new IllegalArgumentException("quantity: mayor que 0");
        if (unitPrice == null || unitPrice.signum() <= 0) throw new IllegalArgumentException("unitPrice: mayor que 0");
        if (lineTotal == null || lineTotal.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) != 0) {
            throw new IllegalArgumentException("lineTotal: debe ser unitPrice × quantity");
        }
    }
}
