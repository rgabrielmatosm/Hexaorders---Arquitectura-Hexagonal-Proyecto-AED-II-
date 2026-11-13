package com.polymer.application.port.out;

import com.polymer.domain.payment.Payment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Intentos de pago en línea. La base de datos garantiza un único pago aprobado por pedido. */
public interface PaymentRepository {
    void save(Payment payment);
    Optional<Payment> find(UUID paymentId);
    List<Payment> forOrder(UUID orderId);
    /** Siguiente correlativo de boleta (secuencia de la base de datos, sin huecos por concurrencia). */
    long nextReceiptNumber();
    /** Cambia el método de pago del pedido al que realmente se usó (p. ej. de Yape a tarjeta). */
    void recordMethodUsed(UUID orderId, com.polymer.domain.fulfillment.PaymentMethod method);
}
