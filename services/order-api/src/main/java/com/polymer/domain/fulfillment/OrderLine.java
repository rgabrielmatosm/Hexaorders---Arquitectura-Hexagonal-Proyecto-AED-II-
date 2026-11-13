package com.polymer.domain.fulfillment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Line of a shop order. Name and price are snapshots taken when the order is placed. v6: when the
 * line was discounted, listUnitPrice keeps the crossed-out price and promotionId the campaign.
 */
public record OrderLine(int lineNo, long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
                        BigDecimal listUnitPrice, Long promotionId) {
    public OrderLine {
        Objects.requireNonNull(productName, "productName");
        if (quantity < 1) throw new IllegalArgumentException("quantity: mayor que 0");
        if (unitPrice == null || unitPrice.signum() <= 0) throw new IllegalArgumentException("unitPrice: mayor que 0");
        if (lineTotal == null || lineTotal.compareTo(unitPrice.multiply(BigDecimal.valueOf(quantity))) != 0) {
            throw new IllegalArgumentException("lineTotal: debe ser unitPrice × quantity");
        }
        if (listUnitPrice != null && listUnitPrice.compareTo(unitPrice) <= 0) listUnitPrice = null;
    }

    /** v2–v5 constructor: a line without discount. */
    public OrderLine(int lineNo, long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
        this(lineNo, productId, productName, unitPrice, quantity, lineTotal, null, null);
    }

    /** Savings of the whole line against the crossed-out price. */
    public BigDecimal savings() {
        return listUnitPrice == null ? BigDecimal.ZERO.setScale(2) : listUnitPrice.subtract(unitPrice).multiply(BigDecimal.valueOf(quantity));
    }
}
