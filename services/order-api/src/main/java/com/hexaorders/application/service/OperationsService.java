package com.hexaorders.application.service;

import com.hexaorders.application.exception.ResourceNotFoundException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.OperationsUseCases;
import com.hexaorders.application.port.in.OrderDetails;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.BusinessTelemetry;
import com.hexaorders.application.port.out.CommerceOrderRepository;
import com.hexaorders.application.port.out.DeliveryRepository;
import com.hexaorders.application.port.out.IncidentRepository;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.TransitionPolicy;
import com.hexaorders.domain.incident.Incident;
import com.hexaorders.domain.incident.IncidentCategory;
import com.hexaorders.domain.incident.IncidentStatus;
import com.hexaorders.domain.identity.Role;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class OperationsService implements OperationsUseCases {
    private final CommerceOrderRepository orders;
    private final DeliveryRepository deliveries;
    private final IncidentRepository incidents;
    private final BusinessTelemetry telemetry;
    private final AuditLog audit;
    private final Clock clock;
    private final LifecycleTransitions transitions;

    public OperationsService(CommerceOrderRepository orders, DeliveryRepository deliveries, IncidentRepository incidents,
                             BusinessTelemetry telemetry, AuditLog audit, Clock clock) {
        this.orders = orders;
        this.deliveries = deliveries;
        this.incidents = incidents;
        this.telemetry = telemetry;
        this.audit = audit;
        this.clock = clock;
        this.transitions = new LifecycleTransitions(orders, deliveries, telemetry, audit, clock);
    }

    @Override
    public List<CommerceOrder> queue(AuthenticatedUser operator, Set<FulfillmentStatus> statuses) {
        Guards.require(operator, Role.ORDER_OPERATOR, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        Set<FulfillmentStatus> wanted = statuses == null || statuses.isEmpty() ? EnumSet.allOf(FulfillmentStatus.class) : statuses;
        return orders.byStatuses(wanted, 200);
    }

    @Override
    public OrderDetails details(AuthenticatedUser operator, UUID orderId) {
        Guards.require(operator, Role.ORDER_OPERATOR, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        CommerceOrder order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return new OrderDetails(order, orders.history(orderId), deliveries.findByOrder(orderId).orElse(null),
                TransitionPolicy.nextFor(order.status(), operator.actors()));
    }

    @Override
    public CommerceOrder transition(AuthenticatedUser operator, UUID orderId, FulfillmentStatus target, String note) {
        Guards.require(operator, Role.ORDER_OPERATOR, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        return transitions.apply(operator, operator.actors(), orderId, target, note);
    }

    @Override
    public Incident reportIncident(AuthenticatedUser reporter, UUID orderId, IncidentCategory category, String description) {
        Guards.require(reporter, Role.ORDER_OPERATOR, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        if (category == null) throw new IllegalArgumentException("category: obligatoria");
        Incident.validateDescription(description);
        if (orderId != null && orders.findById(orderId).isEmpty()) throw new ResourceNotFoundException("Pedido no encontrado");
        Incident incident = incidents.create(orderId, category, description.strip(), reporter.username(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        telemetry.incidentReported(category);
        audit.record(reporter.username(), "INCIDENT_REPORTED", "incident:" + incident.id(),
                category + (orderId == null ? "" : " · order " + orderId));
        return incident;
    }

    @Override
    public List<Incident> incidents(AuthenticatedUser operator, IncidentStatus status) {
        Guards.require(operator, Role.ORDER_OPERATOR, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        return incidents.list(status);
    }

    @Override
    public Incident resolveIncident(AuthenticatedUser operator, long incidentId, String resolution) {
        Guards.require(operator, Role.ORDER_OPERATOR, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        String clean = Guards.text(resolution, 500, "resolution");
        if (clean == null) throw new IllegalArgumentException("resolution: obligatoria");
        Incident incident = incidents.resolve(incidentId, clean, clock.instant().truncatedTo(ChronoUnit.MICROS))
                .orElseThrow(() -> new ResourceNotFoundException("Incidencia no encontrada o ya resuelta"));
        audit.record(operator.username(), "INCIDENT_RESOLVED", "incident:" + incidentId, clean);
        return incident;
    }
}
