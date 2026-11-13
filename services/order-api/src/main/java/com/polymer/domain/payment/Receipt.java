package com.polymer.domain.payment;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Boleta de venta (comprobante interno de la tienda de demostración; no es un comprobante electrónico
 * de SUNAT). Serie B001 y número correlativo; el total incluye IGV.
 */
public record Receipt(String series, long number, BigDecimal taxableBase, BigDecimal igv, BigDecimal total) {
    public static final String SERIES = "B001";

    public Receipt {
        if (series == null || !series.matches("[A-Z]\\d{3}")) throw new IllegalArgumentException("series: no válida");
        if (number < 1) throw new IllegalArgumentException("number: correlativo positivo");
        Objects.requireNonNull(total, "total");
        if (taxableBase.add(igv).compareTo(total) != 0) throw new IllegalArgumentException("La base más el IGV debe ser el total");
    }

    public static Receipt issue(long number, BigDecimal total) {
        return new Receipt(SERIES, number, PaymentPolicy.taxableBase(total), PaymentPolicy.igv(total), total);
    }

    /** «B001-00000123». */
    public String code() { return series + "-" + String.format("%08d", number); }
}
