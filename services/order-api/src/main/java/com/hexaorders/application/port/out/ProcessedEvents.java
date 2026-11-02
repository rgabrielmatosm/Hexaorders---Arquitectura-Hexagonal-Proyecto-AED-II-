package com.hexaorders.application.port.out;

/** Remembers which events a consumer already handled (idempotent consumers, duplicates ignored). */
public interface ProcessedEvents {
    boolean alreadyProcessed(String eventId, String consumer);
    void markProcessed(String eventId, String consumer);
}
