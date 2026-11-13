package com.polymer.application.port.out;

import com.polymer.domain.event.BusinessEvent;

/** Publishes an event to the message broker and returns only after the broker confirmed it. */
public interface EventPublisher {
    void publish(BusinessEvent event);
}
