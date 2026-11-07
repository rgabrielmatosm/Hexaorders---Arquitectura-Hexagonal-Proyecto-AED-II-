package com.hexaorders.domain;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** A validated request in the business language; no framework types. Currency: USD (original API) or PEN (store). */
public record OrderDraft(String customerId, BigDecimal amount, String currency) {
    private static final Pattern CUSTOMER_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000.00");

    public OrderDraft {
        if (customerId == null || !CUSTOMER_ID.matcher(customerId).matches()) {
            throw new IllegalArgumentException("customerId: use entre 1 y 64 letras, dígitos, guiones o guiones bajos");
        }
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("amount: debe ser mayor que 0 y menor o igual que 1000000.00");
        }
        if (amount.scale() > 2) {
            throw new IllegalArgumentException("amount: se admiten como máximo 2 decimales; no se redondean importes");
        }
        // v6: the store sells in soles (PEN); the original order API keeps accepting only USD (OrderService).
        if (!"USD".equals(currency) && !"PEN".equals(currency)) {
            throw new IllegalArgumentException("currency: la única moneda admitida es USD");
        }
        amount = amount.setScale(2);
    }
}
