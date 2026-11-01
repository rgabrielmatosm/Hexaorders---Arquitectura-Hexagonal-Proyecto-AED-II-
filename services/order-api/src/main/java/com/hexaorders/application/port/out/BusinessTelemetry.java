package com.hexaorders.application.port.out;

import com.hexaorders.domain.OrderStatus;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.incident.IncidentCategory;

/** Low-cardinality business metrics of the platform (no user or order identifiers as labels). */
public interface BusinessTelemetry {
    void shopOrderPlaced(OrderStatus riskDecision);
    void checkoutFailed(String reason);
    void transition(FulfillmentStatus from, FulfillmentStatus to);
    void deliveryScheduled(boolean rescheduled);
    void deliveryConflict();
    void incidentReported(IncidentCategory category);
}
