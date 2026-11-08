package com.polymer.domain.fulfillment;

import com.polymer.domain.OrderStatus;
import java.util.EnumSet;
import java.util.Set;

/**
 * Order lifecycle. The original risk decision (OrderStatus APPROVED/REVIEW) is preserved and
 * mapped to RISK_APPROVED / RISK_REVIEW; the rest of the flow extends it.
 */
public enum FulfillmentStatus {
    CREATED, RISK_APPROVED, RISK_REVIEW, CONFIRMED, PREPARING, READY_FOR_DISPATCH, IN_TRANSIT,
    DELIVERED, REJECTED, CANCELLED;

    private static final Set<FulfillmentStatus> TERMINAL = EnumSet.of(DELIVERED, REJECTED, CANCELLED);
    private static final Set<FulfillmentStatus> BEFORE_DISPATCH =
            EnumSet.of(CREATED, RISK_APPROVED, RISK_REVIEW, CONFIRMED, PREPARING, READY_FOR_DISPATCH);

    public static FulfillmentStatus fromRiskDecision(OrderStatus decision) {
        return decision == OrderStatus.REVIEW ? RISK_REVIEW : RISK_APPROVED;
    }

    public boolean isTerminal() { return TERMINAL.contains(this); }

    /** Cancelling or rejecting before dispatch returns the reserved stock to the catalog. */
    public boolean releasesStockWhenMovingTo(FulfillmentStatus target) {
        return BEFORE_DISPATCH.contains(this) && (target == CANCELLED || target == REJECTED);
    }
}
