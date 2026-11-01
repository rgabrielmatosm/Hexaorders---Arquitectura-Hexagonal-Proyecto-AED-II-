package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.CommerceOrder;

/** created=false when the same Idempotency-Key was already processed (the original order is returned). */
public record PlacementResult(CommerceOrder order, boolean created) {}
