package com.polymer.domain.payment;

/** Marca de la tarjeta según su prefijo (IIN). */
public enum CardBrand {
    VISA, MASTERCARD, AMEX, DINERS, OTHER;

    public static CardBrand of(String digits) {
        if (digits.startsWith("4")) return VISA;
        if (digits.matches("^(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d{2}|27[01]\\d|2720).*")) return MASTERCARD;
        if (digits.matches("^3[47].*")) return AMEX;
        if (digits.matches("^3(0[0-5]|[689]).*")) return DINERS;
        return OTHER;
    }
}
