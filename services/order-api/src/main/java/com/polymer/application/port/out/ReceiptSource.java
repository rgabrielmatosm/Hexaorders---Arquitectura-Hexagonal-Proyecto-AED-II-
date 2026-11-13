package com.polymer.application.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Datos de la boleta de un pedido pagado en línea, para el correo (el evento no lleva datos personales). */
public interface ReceiptSource {
    Optional<ReceiptMail> forOrder(UUID orderId);

    record ReceiptMail(String code, Instant paidAt, String method, String reference, String cardBrand, String cardLast4, List<Line> lines,
                       BigDecimal subtotal, BigDecimal savings, BigDecimal shippingCost, BigDecimal taxableBase, BigDecimal igv,
                       BigDecimal total, String currency, String delivery) {}

    record Line(String product, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
}
