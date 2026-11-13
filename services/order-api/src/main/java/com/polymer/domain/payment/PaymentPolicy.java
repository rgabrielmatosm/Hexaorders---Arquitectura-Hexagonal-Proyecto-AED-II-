package com.polymer.domain.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;

/** Reglas de negocio de los pagos en línea. */
public final class PaymentPolicy {
    /** Vigencia del código QR / solicitud de Yape y Plin. */
    public static final Duration WALLET_TTL = Duration.ofSeconds(60);
    /** Intentos rechazados por pedido antes de bloquear el pago en línea (evita probar tarjetas robadas). */
    public static final int MAX_DECLINED_ATTEMPTS = 5;
    /** Monto máximo por operación con Yape/Plin (límite habitual de las billeteras). */
    public static final BigDecimal WALLET_LIMIT = new BigDecimal("2000.00");
    /** IGV del Perú (incluido en los precios). */
    public static final BigDecimal IGV_RATE = new BigDecimal("0.18");

    private PaymentPolicy() {}

    /** Precio con IGV → base imponible (2 decimales); el IGV es la diferencia, así base + IGV = total exacto. */
    public static BigDecimal taxableBase(BigDecimal total) {
        return total.divide(BigDecimal.ONE.add(IGV_RATE), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal igv(BigDecimal total) {
        return total.subtract(taxableBase(total));
    }
}
