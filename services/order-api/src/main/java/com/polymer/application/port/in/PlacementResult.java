package com.polymer.application.port.in;

import com.polymer.domain.fulfillment.CommerceOrder;

/** created=false when the same Idempotency-Key was already processed (the original order is returned). */
public record PlacementResult(CommerceOrder order, boolean created) {}
