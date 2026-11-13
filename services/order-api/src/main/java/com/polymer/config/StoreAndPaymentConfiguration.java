package com.polymer.config;

import com.polymer.adapter.out.cache.RedisPaymentWindow;
import com.polymer.adapter.out.payment.ClockPaymentWindow;
import com.polymer.adapter.out.payment.SandboxCardGateway;
import com.polymer.adapter.out.payment.SandboxWalletGateway;
import com.polymer.adapter.out.persistence.JdbcPaymentRepository;
import com.polymer.application.port.in.PaymentUseCases;
import com.polymer.application.port.in.StoreUseCases;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CardGateway;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.PaymentRepository;
import com.polymer.application.port.out.PaymentWindow;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.port.out.WalletGateway;
import com.polymer.application.service.PaymentService;
import com.polymer.application.service.StoreService;
import com.polymer.domain.store.BusinessHours;
import com.polymer.domain.store.StoreProfile;
import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * v7 · Composition root de la tienda física (mapa y horario) y de los pagos en línea. Las pasarelas son
 * de PRUEBAS (no cobran): sustituir SandboxCardGateway/SandboxWalletGateway por adaptadores reales no
 * cambia el núcleo. La ventana de Yape/Plin usa Redis (TTL) cuando existe.
 */
@Configuration(proxyBeanMethods = false)
public class StoreAndPaymentConfiguration {
    /** Zona horaria de la tienda (horario de atención, vencimiento de tarjetas). */
    @Bean ZoneId storeZone(@Value("${POLYMER_STORE_TIMEZONE:America/Lima}") String zone) { return ZoneId.of(zone); }

    /**
     * Dirección de demostración configurable. Por defecto, un centro comercial conocido de Lima para que
     * el mapa muestre una ubicación real; cámbiala con las variables POLYMER_STORE_* (ver .env.example).
     */
    @Bean
    StoreProfile storeProfile(@Value("${POLYMER_STORE_NAME:POLYMER}") String name,
                              @Value("${POLYMER_STORE_ADDRESS:Av. Javier Prado Este 4200}") String address,
                              @Value("${POLYMER_STORE_DISTRICT:Santiago de Surco}") String district,
                              @Value("${POLYMER_STORE_CITY:Lima}") String city,
                              @Value("${POLYMER_STORE_COUNTRY:Perú}") String country,
                              @Value("${POLYMER_STORE_LATITUDE:-12.0853}") double latitude,
                              @Value("${POLYMER_STORE_LONGITUDE:-76.9753}") double longitude,
                              @Value("${POLYMER_STORE_PHONE:+51 900 000 000}") String phone,
                              @Value("${POLYMER_STORE_EMAIL:ventas@polymer.local}") String email) {
        return new StoreProfile(name, address, district, city, country, latitude, longitude, phone, email);
    }

    @Bean
    StoreUseCases storeUseCases(StoreProfile profile, Clock clock, ZoneId storeZone,
                                @Value("${POLYMER_GOOGLE_CALENDAR_ID:}") String calendarId) {
        return new StoreService(profile, BusinessHours.standard(), clock, storeZone, calendarId);
    }

    @Bean PaymentRepository paymentRepository(JdbcTemplate jdbc) { return new JdbcPaymentRepository(jdbc); }
    @Bean CardGateway cardGateway() { return new SandboxCardGateway(); }
    @Bean WalletGateway walletGateway() { return new SandboxWalletGateway(); }

    @Bean
    @ConditionalOnProperty(name = "polymer.integrations.redis.enabled", havingValue = "true")
    PaymentWindow redisPaymentWindow(StringRedisTemplate redis, Clock clock) { return new RedisPaymentWindow(redis, clock); }

    @Bean
    @ConditionalOnProperty(name = "polymer.integrations.redis.enabled", havingValue = "false", matchIfMissing = true)
    PaymentWindow clockPaymentWindow(Clock clock) { return new ClockPaymentWindow(clock); }

    @Bean
    PaymentUseCases paymentUseCases(CommerceOrderRepository orders, PaymentRepository payments, CardGateway cards, WalletGateway wallets,
                                    PaymentWindow window, UserRepository users, BusinessTelemetry telemetry, AuditLog audit, Clock clock,
                                    ZoneId storeZone) {
        return new PaymentService(orders, payments, cards, wallets, window, users, telemetry, audit, clock, storeZone, true);
    }
}
