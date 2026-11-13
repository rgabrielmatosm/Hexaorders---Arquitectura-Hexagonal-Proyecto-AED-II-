package com.polymer.application.port.in;

import com.polymer.domain.fulfillment.PaymentMethod;
import com.polymer.domain.payment.CardDetails;
import java.time.YearMonth;
import java.util.UUID;

/** v7: pago en línea de un pedido de la tienda (tarjeta al instante; Yape/Plin con QR de 60 s) y su boleta. */
public interface PaymentUseCases {
    /** Estado de pago del pedido (pagado, pendiente con segundos restantes, vencido…). */
    PaymentView status(AuthenticatedUser customer, UUID orderId);

    PaymentView payWithCard(AuthenticatedUser customer, UUID orderId, CardDetails card);

    /** Genera una solicitud de Yape o Plin con su QR, válida 60 s (la anterior pendiente se anula). */
    PaymentView startWallet(AuthenticatedUser customer, UUID orderId, PaymentMethod wallet);

    PaymentView confirmWallet(AuthenticatedUser customer, UUID orderId, UUID paymentId, String phone, String approvalCode);

    ReceiptView receipt(AuthenticatedUser customer, UUID orderId);

    /** Mes actual en la zona horaria de la tienda (para validar el vencimiento de la tarjeta). */
    YearMonth currentMonth();
}
