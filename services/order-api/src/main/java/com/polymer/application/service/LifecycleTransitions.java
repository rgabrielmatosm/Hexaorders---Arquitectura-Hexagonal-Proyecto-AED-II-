package com.polymer.application.service;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.DeliveryRepository;
import com.polymer.domain.fulfillment.Actor;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.StatusChange;
import com.polymer.domain.fulfillment.TransitionPolicy;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

/** Shared transition logic for operators, logistics and customers. */
final class LifecycleTransitions {
    private final CommerceOrderRepository orders;
    private final DeliveryRepository deliveries;
    private final BusinessTelemetry telemetry;
    private final AuditLog audit;
    private final Clock clock;
    private final com.polymer.application.port.out.CatalogCache cache;

    LifecycleTransitions(CommerceOrderRepository orders, DeliveryRepository deliveries, BusinessTelemetry telemetry,
                         AuditLog audit, Clock clock) {
        this(orders, deliveries, telemetry, audit, clock, com.polymer.application.port.out.CatalogCache.NONE);
    }

    LifecycleTransitions(CommerceOrderRepository orders, DeliveryRepository deliveries, BusinessTelemetry telemetry,
                         AuditLog audit, Clock clock, com.polymer.application.port.out.CatalogCache cache) {
        this.cache = cache;
        this.orders = orders;
        this.deliveries = deliveries;
        this.telemetry = telemetry;
        this.audit = audit;
        this.clock = clock;
    }

    CommerceOrder apply(AuthenticatedUser user, Set<Actor> actors, UUID orderId, FulfillmentStatus target, String note) {
        if (target == null) throw new IllegalArgumentException("status: obligatorio");
        String cleanNote = Guards.text(note, 500, "note");
        CommerceOrder order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        if (order.status() == target) {
            return order; // idempotent: repeating the same transition does not fail or duplicate history
        }
        TransitionPolicy.check(order.status(), target, actors);
        if (target == FulfillmentStatus.IN_TRANSIT && deliveries.findByOrder(orderId).isEmpty()) {
            throw new BusinessConflictException("DELIVERY_NOT_SCHEDULED", "Programa la entrega en el calendario antes de despacharla");
        }
        boolean releaseStock = "SHOP".equals(order.channel()) && order.status().releasesStockWhenMovingTo(target);
        StatusChange change = new StatusChange(order.status(), target, user.username(), cleanNote,
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (!orders.transition(orderId, order.version(), order.status(), change, releaseStock)) {
            throw new BusinessConflictException("CONCURRENT_UPDATE", "El pedido fue modificado por otra persona; recarga y vuelve a intentarlo");
        }
        if (releaseStock) cache.invalidate(); // units went back to stock: cached listings are stale
        telemetry.transition(order.status(), target);
        audit.record(user.username(), "ORDER_STATUS_CHANGED", orderId.toString(),
                order.status() + " -> " + target + (cleanNote == null ? "" : " · " + cleanNote));
        return orders.findById(orderId).orElseThrow();
    }
}
