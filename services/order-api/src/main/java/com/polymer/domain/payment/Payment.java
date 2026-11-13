package com.polymer.domain.payment;

import com.polymer.domain.fulfillment.PaymentMethod;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un intento de pago en línea de un pedido. Tarjeta: se resuelve al instante (PAID o DECLINED).
 * Yape/Plin: nace PENDING con una ventana de 60 s; si no se aprueba a tiempo, EXPIRED.
 */
public record Payment(UUID id, UUID orderId, PaymentMethod method, BigDecimal amount, String currency, PaymentStatus status,
                      Instant createdAt, Instant expiresAt, Instant paidAt, String reference, String cardBrand, String cardLast4,
                      String failureCode, Receipt receipt) {
    public Payment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(orderId, "orderId");
        Objects.requireNonNull(method, "method");
        if (!method.online()) throw new IllegalArgumentException("method: no es un pago en línea");
        if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("amount: debe ser mayor que cero");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == PaymentStatus.PAID && (paidAt == null || receipt == null)) throw new IllegalStateException("Un pago aprobado tiene fecha y boleta");
    }

    public static Payment wallet(UUID id, UUID orderId, PaymentMethod method, BigDecimal amount, String currency, Instant now) {
        if (method != PaymentMethod.YAPE && method != PaymentMethod.PLIN) throw new IllegalArgumentException("method: YAPE o PLIN");
        if (amount.compareTo(PaymentPolicy.WALLET_LIMIT) > 0) {
            throw new IllegalArgumentException("amount: Yape y Plin admiten hasta S/ 2000 por operación; paga con tarjeta");
        }
        return new Payment(id, orderId, method, amount, currency, PaymentStatus.PENDING, now, now.plus(PaymentPolicy.WALLET_TTL),
                null, null, null, null, null, null);
    }

    public static Payment card(UUID id, UUID orderId, BigDecimal amount, String currency, CardDetails card, Instant now) {
        return new Payment(id, orderId, PaymentMethod.CARD, amount, currency, PaymentStatus.PENDING, now, null, null, null,
                card.brand().name(), card.last4(), null, null);
    }

    /** Ventana de Yape/Plin vencida según el reloj (fuente de verdad si Redis no está). */
    public boolean windowElapsed(Instant now) { return expiresAt != null && !now.isBefore(expiresAt); }

    public Payment approved(String operationReference, Receipt issued, Instant at) {
        if (status != PaymentStatus.PENDING) throw new IllegalStateException("Solo un pago pendiente puede aprobarse");
        return new Payment(id, orderId, method, amount, currency, PaymentStatus.PAID, createdAt, expiresAt, at, operationReference,
                cardBrand, cardLast4, null, issued);
    }

    public Payment declined(String code) {
        return new Payment(id, orderId, method, amount, currency, PaymentStatus.DECLINED, createdAt, expiresAt, null, null,
                cardBrand, cardLast4, code, null);
    }

    public Payment expired() {
        return new Payment(id, orderId, method, amount, currency, PaymentStatus.EXPIRED, createdAt, expiresAt, null, null,
                cardBrand, cardLast4, "WINDOW_EXPIRED", null);
    }

    public Payment cancelled() {
        return new Payment(id, orderId, method, amount, currency, PaymentStatus.CANCELLED, createdAt, expiresAt, null, null,
                cardBrand, cardLast4, "REPLACED", null);
    }
}
