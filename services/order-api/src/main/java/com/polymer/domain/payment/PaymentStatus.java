package com.polymer.domain.payment;

/** Estado de un intento de pago en línea. Solo puede haber un pago PAID por pedido. */
public enum PaymentStatus {
    /** Yape/Plin: código QR generado, esperando la aprobación del cliente (máximo 60 s). */
    PENDING,
    PAID,
    DECLINED,
    /** Yape/Plin: venció la ventana de 60 s sin aprobación. */
    EXPIRED,
    CANCELLED
}
