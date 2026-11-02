package com.hexaorders.adapter.in.scheduling;

import com.hexaorders.application.port.in.EventRelayUseCases;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Driving adapter: a timer that asks the relay use case to publish pending outbox events. */
public final class EventRelayScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(EventRelayScheduler.class);
    private final EventRelayUseCases relay;
    private long lastWarning;

    public EventRelayScheduler(EventRelayUseCases relay) { this.relay = relay; }

    @Scheduled(fixedDelayString = "${hexaorders.events.relay-interval-ms:2000}", initialDelayString = "${hexaorders.events.relay-initial-delay-ms:5000}")
    public void tick() {
        try {
            int published = relay.relayPending();
            if (published > 0) LOG.info("Eventos publicados en RabbitMQ: {}", published);
        } catch (RuntimeException failure) {
            long now = System.currentTimeMillis();
            if (now - lastWarning > 60_000) { // one warning per minute is enough while a dependency is down
                LOG.warn("No se pudieron publicar los eventos pendientes: {}", failure.getMessage());
                lastWarning = now;
            }
        }
    }
}
