package com.polymer.domain.payment;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Datos de tarjeta para UNA autorización. Nunca se guardan: del pago solo quedan la marca y los
 * cuatro últimos dígitos. toString() no revela el número ni el CVV.
 */
public final class CardDetails {
    private final String number;
    private final String holder;
    private final YearMonth expiry;
    private final String cvv;

    private CardDetails(String number, String holder, YearMonth expiry, String cvv) {
        this.number = number;
        this.holder = holder;
        this.expiry = expiry;
        this.cvv = cvv;
    }

    /** Valida formato, dígito verificador (Luhn), vencimiento y CVV. today: mes actual (zona del comercio). */
    public static CardDetails of(String number, String holder, Integer month, Integer year, String cvv, YearMonth today) {
        String digits = number == null ? "" : number.replaceAll("[\\s-]", "");
        if (!digits.matches("\\d{13,19}") || !luhn(digits)) throw new IllegalArgumentException("cardNumber: el número de tarjeta no es válido");
        String name = holder == null ? "" : holder.strip();
        if (name.length() < 3 || name.length() > 60) throw new IllegalArgumentException("cardHolder: escribe el nombre como figura en la tarjeta");
        if (month == null || year == null || month < 1 || month > 12) throw new IllegalArgumentException("expiry: fecha de vencimiento no válida");
        int fullYear = year < 100 ? 2000 + year : year;
        YearMonth expiry = YearMonth.of(fullYear, month);
        if (expiry.isBefore(Objects.requireNonNull(today))) throw new IllegalArgumentException("expiry: la tarjeta está vencida");
        if (expiry.isAfter(today.plusYears(20))) throw new IllegalArgumentException("expiry: fecha de vencimiento no válida");
        CardBrand brand = CardBrand.of(digits);
        String code = cvv == null ? "" : cvv.strip();
        if (!code.matches(brand == CardBrand.AMEX ? "\\d{4}" : "\\d{3}")) throw new IllegalArgumentException("cvv: código de seguridad no válido");
        return new CardDetails(digits, name, expiry, code);
    }

    /** Algoritmo de Luhn (ISO/IEC 7812): detecta errores de tipeo en el número. */
    public static boolean luhn(String digits) {
        int sum = 0;
        boolean twice = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int d = digits.charAt(i) - '0';
            if (twice) {
                d *= 2;
                if (d > 9) d -= 9;
            }
            sum += d;
            twice = !twice;
        }
        return sum % 10 == 0;
    }

    public CardBrand brand() { return CardBrand.of(number); }
    public String last4() { return number.substring(number.length() - 4); }
    public String holder() { return holder; }
    public YearMonth expiry() { return expiry; }
    /** Solo para el adaptador de la pasarela (no se guarda ni se registra). */
    public String number() { return number; }
    public String cvv() { return cvv; }

    @Override
    public String toString() { return brand() + " •••• " + last4(); }
}
