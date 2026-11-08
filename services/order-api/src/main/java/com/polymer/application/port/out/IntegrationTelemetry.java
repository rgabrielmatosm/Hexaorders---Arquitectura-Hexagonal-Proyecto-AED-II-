package com.polymer.application.port.out;

import com.polymer.domain.event.EventType;

/** Metrics of the asynchronous integrations (events and notifications). */
public interface IntegrationTelemetry {
    void eventPublished(EventType type);
    void eventPublishFailed();
    void eventConsumed(EventType type, String result);
    void notificationSent(EventType type);

    IntegrationTelemetry NONE = new IntegrationTelemetry() {
        @Override public void eventPublished(EventType type) { }
        @Override public void eventPublishFailed() { }
        @Override public void eventConsumed(EventType type, String result) { }
        @Override public void notificationSent(EventType type) { }
    };
}
