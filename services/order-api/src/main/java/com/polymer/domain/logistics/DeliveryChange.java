package com.polymer.domain.logistics;

import java.time.Instant;

/** Audit trail of a delivery: creation and every rescheduling. */
public record DeliveryChange(long deliveryId, String actor, String action, Instant oldStart, Instant oldEnd,
                             String oldAssignee, Instant newStart, Instant newEnd, String newAssignee,
                             String reason, Instant at) {}
