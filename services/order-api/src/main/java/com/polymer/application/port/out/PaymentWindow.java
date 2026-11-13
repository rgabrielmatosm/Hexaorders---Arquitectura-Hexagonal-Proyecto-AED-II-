package com.polymer.application.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Ventana de vigencia de una solicitud de Yape/Plin. Con Redis es una clave con TTL de 60 s (Redis la
 * borra sola al vencer); sin Redis se compara el reloj con expiresAt. expiresAt (guardado en
 * PostgreSQL) es siempre el respaldo si Redis falla.
 */
public interface PaymentWindow {
    void open(UUID paymentId, Duration ttl);
    boolean isOpen(UUID paymentId, Instant expiresAt);
    /** Segundos restantes (0 si venció). */
    long remainingSeconds(UUID paymentId, Instant expiresAt);
    void close(UUID paymentId);
}
