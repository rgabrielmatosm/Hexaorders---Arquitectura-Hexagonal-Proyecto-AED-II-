package com.hexaorders.adapter.out.metrics;

import com.hexaorders.application.port.out.IntegrationTelemetry;
import com.hexaorders.domain.event.EventType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Counters are pre-registered so the dashboards show 0 instead of "No data" before the first event. */
public final class MicrometerIntegrationTelemetry implements IntegrationTelemetry {
    private final MeterRegistry registry;

    public MicrometerIntegrationTelemetry(MeterRegistry registry) {
        this.registry = registry;
        for (EventType type : EventType.values()) {
            published(type);
            sent(type);
            for (String result : new String[] {"sent", "duplicate", "skipped"}) consumed(type, result);
        }
        failures();
    }

    private Counter published(EventType type) {
        return Counter.builder("hexaorders_events_published").description("Eventos confirmados por RabbitMQ").tag("type", type.name()).register(registry);
    }

    private Counter failures() {
        return Counter.builder("hexaorders_events_publish_failures").description("Intentos de publicación fallidos (el evento sigue en el outbox)").register(registry);
    }

    private Counter consumed(EventType type, String result) {
        return Counter.builder("hexaorders_events_consumed").description("Eventos consumidos por resultado")
                .tag("type", type.name()).tag("result", result).register(registry);
    }

    private Counter sent(EventType type) {
        return Counter.builder("hexaorders_notifications_sent").description("Correos aceptados por el servidor SMTP").tag("type", type.name()).register(registry);
    }

    @Override public void eventPublished(EventType type) { published(type).increment(); }
    @Override public void eventPublishFailed() { failures().increment(); }
    @Override public void eventConsumed(EventType type, String result) { consumed(type, result).increment(); }
    @Override public void notificationSent(EventType type) { sent(type).increment(); }
}
