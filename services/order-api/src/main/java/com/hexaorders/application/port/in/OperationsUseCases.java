package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.incident.Incident;
import com.hexaorders.domain.incident.IncidentCategory;
import com.hexaorders.domain.incident.IncidentStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface OperationsUseCases {
    List<CommerceOrder> queue(AuthenticatedUser operator, Set<FulfillmentStatus> statuses);
    OrderDetails details(AuthenticatedUser operator, UUID orderId);
    /** Validated with TransitionPolicy; idempotent when the order is already in the target state. */
    CommerceOrder transition(AuthenticatedUser operator, UUID orderId, FulfillmentStatus target, String note);
    Incident reportIncident(AuthenticatedUser reporter, UUID orderId, IncidentCategory category, String description);
    List<Incident> incidents(AuthenticatedUser operator, IncidentStatus status);
    Incident resolveIncident(AuthenticatedUser operator, long incidentId, String resolution);
}
