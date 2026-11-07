package com.hexaorders.config;

import com.hexaorders.adapter.in.messaging.BusinessEventListener;
import com.hexaorders.adapter.in.scheduling.EventRelayScheduler;
import com.hexaorders.adapter.messaging.EventContract;
import com.hexaorders.adapter.out.cache.RedisCatalogCache;
import com.hexaorders.adapter.out.mail.SmtpMailSender;
import com.hexaorders.adapter.out.messaging.RabbitMqEventPublisher;
import com.hexaorders.adapter.out.metrics.MicrometerIntegrationTelemetry;
import com.hexaorders.adapter.out.persistence.JdbcEventOutbox;
import com.hexaorders.adapter.out.persistence.JdbcNotificationRecipients;
import com.hexaorders.adapter.out.persistence.JdbcProcessedEvents;
import com.hexaorders.application.port.in.EventRelayUseCases;
import com.hexaorders.application.port.in.NotificationUseCases;
import com.hexaorders.application.port.out.CatalogCache;
import com.hexaorders.application.port.out.EventOutbox;
import com.hexaorders.application.port.out.IntegrationTelemetry;
import com.hexaorders.application.service.EventRelayService;
import com.hexaorders.application.service.NotificationService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Composition root of the v5 integrations (Redis cache, RabbitMQ events, SMTP notifications). */
@Configuration(proxyBeanMethods = false)
public class IntegrationConfiguration {

    @Bean
    IntegrationTelemetry integrationTelemetry(MeterRegistry registry) { return new MicrometerIntegrationTelemetry(registry); }

    /** Without Redis (basic profile, tests) the catalog is read from PostgreSQL every time. */
    @Bean
    @ConditionalOnProperty(name = "hexaorders.integrations.redis.enabled", havingValue = "false", matchIfMissing = true)
    CatalogCache noCatalogCache() { return CatalogCache.NONE; }

    @Bean
    @ConditionalOnProperty(name = "hexaorders.integrations.redis.enabled", havingValue = "true")
    CatalogCache redisCatalogCache(StringRedisTemplate redis, MeterRegistry registry,
                                   @Value("${hexaorders.cache.catalog-ttl:60s}") Duration ttl,
                                   @Value("${hexaorders.cache.backoff:30s}") Duration backoff) {
        return new RedisCatalogCache(redis, ttl, backoff, registry);
    }

    @Bean
    EventOutbox eventOutbox(JdbcTemplate jdbc) { return new JdbcEventOutbox(jdbc); }

    /** Pending events in the outbox (grows while RabbitMQ is down, drains when it returns). */
    @Bean
    MeterBinder outboxGauge(EventOutbox outbox) {
        return registry -> {
            AtomicReference<Double> value = new AtomicReference<>(Double.NaN);
            AtomicReference<Long> loadedAt = new AtomicReference<>(0L);
            Gauge.builder("hexaorders_outbox_pending", () -> {
                long now = System.nanoTime();
                if (now - loadedAt.get() > Duration.ofSeconds(10).toNanos()) {
                    try { value.set((double) outbox.pendingCount()); } catch (RuntimeException unavailable) { value.set(Double.NaN); }
                    loadedAt.set(now);
                }
                return value.get();
            }).description("Eventos del outbox pendientes de confirmar por RabbitMQ").register(registry);
        };
    }

    /** RabbitMQ topology, publisher, relay, consumer and SMTP: only when the broker is deployed. */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "hexaorders.integrations.messaging.enabled", havingValue = "true")
    static class Messaging {
        @Bean
        Declarables eventTopology() {
            TopicExchange events = new TopicExchange(EventContract.EXCHANGE, true, false);
            DirectExchange deadLetters = new DirectExchange(EventContract.DEAD_LETTER_EXCHANGE, true, false);
            Queue notifications = QueueBuilder.durable(EventContract.NOTIFICATIONS_QUEUE)
                    .deadLetterExchange(EventContract.DEAD_LETTER_EXCHANGE)
                    .deadLetterRoutingKey(EventContract.DEAD_LETTER_ROUTING_KEY).build();
            Queue dlq = QueueBuilder.durable(EventContract.NOTIFICATIONS_DLQ).build();
            Binding orders = BindingBuilder.bind(notifications).to(events).with("order.#");
            Binding deliveries = BindingBuilder.bind(notifications).to(events).with("delivery.#");
            Binding dead = BindingBuilder.bind(dlq).to(deadLetters).with(EventContract.DEAD_LETTER_ROUTING_KEY);
            return new Declarables(events, deadLetters, notifications, dlq, orders, deliveries, dead);
        }

        @Bean
        EventRelayUseCases eventRelay(EventOutbox outbox, RabbitTemplate rabbit, IntegrationTelemetry telemetry,
                                      @Value("${hexaorders.events.confirm-timeout-ms:5000}") long timeout) {
            return new EventRelayService(outbox, new RabbitMqEventPublisher(rabbit, timeout), telemetry);
        }

        @Bean
        EventRelayScheduler eventRelayScheduler(EventRelayUseCases relay) { return new EventRelayScheduler(relay); }

        @Bean
        NotificationUseCases notifications(JdbcTemplate jdbc, JavaMailSender smtp, IntegrationTelemetry telemetry,
                                           @Value("${hexaorders.mail.from:Polymer <no-reply@hexaorders.local>}") String from) {
            return new NotificationService(new JdbcProcessedEvents(jdbc), new JdbcNotificationRecipients(jdbc),
                    new SmtpMailSender(smtp, from), telemetry);
        }

        @Bean
        BusinessEventListener businessEventListener(NotificationUseCases notifications) { return new BusinessEventListener(notifications); }
    }
}
