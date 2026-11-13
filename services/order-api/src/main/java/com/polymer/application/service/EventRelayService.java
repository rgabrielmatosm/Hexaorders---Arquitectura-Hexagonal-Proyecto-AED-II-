package com.polymer.application.service;

import com.polymer.application.exception.EventPublishingException;
import com.polymer.application.port.in.EventRelayUseCases;
import com.polymer.application.port.out.EventOutbox;
import com.polymer.application.port.out.EventPublisher;
import com.polymer.application.port.out.IntegrationTelemetry;
import com.polymer.domain.event.BusinessEvent;

/**
 * Polling publisher of the transactional outbox. Delivery is AT-LEAST-ONCE: an event is marked as
 * published only after the broker confirmed it, so a crash in between re-publishes it and the
 * consumer discards the duplicate by eventId. Exactly-once delivery is NOT claimed.
 */
public final class EventRelayService implements EventRelayUseCases {
    static final int BATCH = 50;
    private final EventOutbox outbox;
    private final EventPublisher publisher;
    private final IntegrationTelemetry telemetry;

    public EventRelayService(EventOutbox outbox, EventPublisher publisher, IntegrationTelemetry telemetry) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.telemetry = telemetry;
    }

    @Override
    public int relayPending() {
        int published = 0;
        for (BusinessEvent event : outbox.pending(BATCH)) {
            try {
                publisher.publish(event);
            } catch (EventPublishingException unavailable) {
                // Broker down or not confirming: stop here and retry the same events on the next run.
                telemetry.eventPublishFailed();
                return published;
            }
            outbox.markPublished(event.eventId());
            telemetry.eventPublished(event.type());
            published++;
        }
        return published;
    }
}
