package com.polymer.application.port.in;

import com.polymer.domain.event.BusinessEvent;

/** Reacts to business events by notifying the customer (idempotent: duplicates are ignored). */
public interface NotificationUseCases {
    /** @return "sent", "duplicate" or "skipped" (no recipient, e.g. a lab order). */
    String handle(BusinessEvent event);
}
