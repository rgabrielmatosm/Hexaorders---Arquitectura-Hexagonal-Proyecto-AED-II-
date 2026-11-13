package com.polymer.application.port.out;

import com.polymer.domain.fulfillment.PaymentMethod;
import java.math.BigDecimal;
import java.util.UUID;

/** Billeteras móviles (Yape, Plin): solicitud con QR y confirmación con el código de aprobación de la app. */
public interface WalletGateway {
    /** Contenido del código QR de la solicitud de cobro. */
    String qrPayload(PaymentMethod wallet, UUID paymentId, BigDecimal amount, String currency);

    CardGateway.Result confirm(PaymentMethod wallet, UUID paymentId, String phone, String approvalCode, BigDecimal amount);
}
