package com.polymer.adapter.out.cache;

import com.polymer.application.port.out.PaymentWindow;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Ventana de Yape/Plin en Redis: clave «polymer:payment:window:<id>» con TTL de 60 s. Redis la elimina
 * sola al vencer (EXISTS = 0, TTL = -2), igual que la caché del catálogo. Si Redis falla, se usa la
 * hora de vencimiento guardada en PostgreSQL: el pago nunca queda abierto más de lo debido.
 */
public final class RedisPaymentWindow implements PaymentWindow {
    private static final Logger LOG = LoggerFactory.getLogger(RedisPaymentWindow.class);
    public static final String PREFIX = "polymer:payment:window:";
    private final StringRedisTemplate redis;
    private final Clock clock;

    public RedisPaymentWindow(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    @Override
    public void open(UUID paymentId, Duration ttl) {
        try {
            redis.opsForValue().set(PREFIX + paymentId, "open", ttl);
        } catch (RuntimeException unavailable) {
            LOG.warn("Redis no disponible al abrir la ventana de pago ({}); se usa la hora de vencimiento", unavailable.getClass().getSimpleName());
        }
    }

    @Override
    public boolean isOpen(UUID paymentId, Instant expiresAt) {
        boolean byClock = expiresAt != null && clock.instant().isBefore(expiresAt);
        try {
            return byClock && Boolean.TRUE.equals(redis.hasKey(PREFIX + paymentId));
        } catch (RuntimeException unavailable) {
            return byClock;
        }
    }

    @Override
    public long remainingSeconds(UUID paymentId, Instant expiresAt) {
        try {
            Long ttl = redis.getExpire(PREFIX + paymentId);
            if (ttl != null && ttl >= 0) return ttl;
            if (ttl != null && ttl == -2) return 0; // la clave ya no existe: venció
        } catch (RuntimeException unavailable) {
            // se calcula con el reloj
        }
        if (expiresAt == null) return 0;
        return Math.max(0, (long) Math.ceil(Duration.between(clock.instant(), expiresAt).toMillis() / 1000.0));
    }

    @Override
    public void close(UUID paymentId) {
        try {
            redis.delete(PREFIX + paymentId);
        } catch (RuntimeException unavailable) {
            // vencerá sola por TTL
        }
    }
}
