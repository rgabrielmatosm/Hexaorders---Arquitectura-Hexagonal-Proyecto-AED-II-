package com.hexaorders.domain.fulfillment;

import java.math.BigDecimal;

/** How the order reaches the customer, with its shipping cost rule (PEN). */
public enum DeliveryMethod {
    STANDARD("Envío estándar", "2 a 4 días hábiles", new BigDecimal("15.00"), new BigDecimal("299.00")),
    EXPRESS("Envío express", "24 a 48 horas", new BigDecimal("29.90"), null),
    PICKUP("Recojo en tienda", "Listo en 24 horas hábiles", BigDecimal.ZERO.setScale(2), null);

    private final String label;
    private final String eta;
    private final BigDecimal cost;
    private final BigDecimal freeFrom;

    DeliveryMethod(String label, String eta, BigDecimal cost, BigDecimal freeFrom) {
        this.label = label;
        this.eta = eta;
        this.cost = cost;
        this.freeFrom = freeFrom;
    }

    public String label() { return label; }
    public String eta() { return eta; }
    public BigDecimal baseCost() { return cost; }
    public BigDecimal freeFrom() { return freeFrom; }
    public boolean needsAddress() { return this != PICKUP; }

    /** Standard shipping is free from S/ 299.00 of products. */
    public BigDecimal costFor(BigDecimal subtotal) {
        if (freeFrom != null && subtotal.compareTo(freeFrom) >= 0) return BigDecimal.ZERO.setScale(2);
        return cost;
    }

    public static DeliveryMethod parse(String value) {
        if (value == null || value.isBlank()) return STANDARD;
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("deliveryMethod: STANDARD, EXPRESS o PICKUP");
        }
    }
}
