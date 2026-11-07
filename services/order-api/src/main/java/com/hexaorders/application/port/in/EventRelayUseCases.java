package com.hexaorders.application.port.in;

/** Moves committed events from the outbox to the message broker. */
public interface EventRelayUseCases {
    /** Publishes up to one batch; returns how many events were confirmed by the broker. */
    int relayPending();
}
