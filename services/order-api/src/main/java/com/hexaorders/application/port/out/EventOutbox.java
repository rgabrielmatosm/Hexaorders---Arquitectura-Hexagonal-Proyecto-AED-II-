package com.hexaorders.application.port.out;

import com.hexaorders.domain.event.BusinessEvent;
import java.util.List;

/**
 * Transactional outbox. Events are derived from rows written in the SAME transaction as the
 * business change (order status history and delivery changes), so a committed change always has
 * its event and a rolled back change never has one.
 */
public interface EventOutbox {
    /** Oldest events not yet confirmed by the broker. */
    List<BusinessEvent> pending(int limit);
    void markPublished(String eventId);
    long pendingCount();
}
