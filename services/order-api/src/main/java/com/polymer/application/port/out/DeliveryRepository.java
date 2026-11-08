package com.polymer.application.port.out;

import com.polymer.application.port.in.DeliveryView;
import com.polymer.domain.logistics.Delivery;
import com.polymer.domain.logistics.DeliveryChange;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeliveryRepository {
    Optional<Delivery> findById(long id);
    Optional<Delivery> findByOrder(UUID orderId);
    List<DeliveryView> inRange(Instant from, Instant to, Long assigneeId);
    List<Delivery> overlapping(long assigneeId, Instant start, Instant end, Long excludingId);
    /** Inserts the delivery and its CREATED change in one transaction. */
    Delivery create(Delivery delivery, DeliveryChange change);
    /** Optimistic update (expected version) plus the change record, in one transaction. */
    Optional<Delivery> reschedule(long id, int expectedVersion, long assigneeId, Instant start, Instant end, DeliveryChange change);
    List<DeliveryChange> history(long deliveryId);
}
