package com.polymer.application.port.out;

import com.polymer.domain.OrderStatus;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.incident.IncidentCategory;

/** Low-cardinality business metrics of the platform (no user or order identifiers as labels). */
public interface BusinessTelemetry {
    void shopOrderPlaced(OrderStatus riskDecision);
    void checkoutFailed(String reason);
    void transition(FulfillmentStatus from, FulfillmentStatus to);
    void deliveryScheduled(boolean rescheduled);
    void deliveryConflict();
    void incidentReported(IncidentCategory category);
    /** v7: intentos de pago en línea por método (CARD, YAPE, PLIN) y resultado (paid, declined, expired, started). */
    default void payment(String method, String outcome) {}
}
