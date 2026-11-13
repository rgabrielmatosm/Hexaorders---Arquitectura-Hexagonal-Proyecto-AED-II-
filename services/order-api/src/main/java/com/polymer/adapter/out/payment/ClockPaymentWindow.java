package com.polymer.adapter.out.payment;

import com.polymer.application.port.out.PaymentWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Ventana de pago sin Redis (modo basic y pruebas): compara el reloj con la hora de vencimiento guardada. */
public final class ClockPaymentWindow implements PaymentWindow {
    private final Clock clock;

    public ClockPaymentWindow(Clock clock) { this.clock = clock; }

    @Override
    public void open(UUID paymentId, Duration ttl) { /* expiresAt ya está guardado en PostgreSQL */ }

    @Override
    public boolean isOpen(UUID paymentId, Instant expiresAt) { return expiresAt != null && clock.instant().isBefore(expiresAt); }

    @Override
    public long remainingSeconds(UUID paymentId, Instant expiresAt) {
        if (expiresAt == null) return 0;
        long seconds = (long) Math.ceil(Duration.between(clock.instant(), expiresAt).toMillis() / 1000.0);
        return Math.max(0, seconds);
    }

    @Override
    public void close(UUID paymentId) { /* nada que borrar */ }
}
