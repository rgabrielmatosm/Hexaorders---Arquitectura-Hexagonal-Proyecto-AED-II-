package com.hexaorders.application.service;

import com.hexaorders.application.exception.BusinessConflictException;
import com.hexaorders.application.exception.ResourceNotFoundException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.DeliveryView;
import com.hexaorders.application.port.in.LogisticsUseCases;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.BusinessTelemetry;
import com.hexaorders.application.port.out.CommerceOrderRepository;
import com.hexaorders.application.port.out.DeliveryRepository;
import com.hexaorders.application.port.out.UserRepository;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.identity.Role;
import com.hexaorders.domain.identity.UserAccount;
import com.hexaorders.domain.logistics.Delivery;
import com.hexaorders.domain.logistics.DeliveryChange;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Delivery calendar persisted in PostgreSQL, with conflict validation and change history. */
public final class LogisticsService implements LogisticsUseCases {
    private static final Set<FulfillmentStatus> SCHEDULABLE =
            EnumSet.of(FulfillmentStatus.CONFIRMED, FulfillmentStatus.PREPARING, FulfillmentStatus.READY_FOR_DISPATCH);
    private static final Duration MAX_RANGE = Duration.ofDays(62);

    private final DeliveryRepository deliveries;
    private final CommerceOrderRepository orders;
    private final UserRepository users;
    private final BusinessTelemetry telemetry;
    private final AuditLog audit;
    private final Clock clock;

    public LogisticsService(DeliveryRepository deliveries, CommerceOrderRepository orders, UserRepository users,
                            BusinessTelemetry telemetry, AuditLog audit, Clock clock) {
        this.deliveries = deliveries;
        this.orders = orders;
        this.users = users;
        this.telemetry = telemetry;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<DeliveryView> calendar(AuthenticatedUser user, Instant from, Instant to, Long assigneeId) {
        Guards.require(user, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        if (from == null || to == null || !to.isAfter(from)) throw new IllegalArgumentException("Rango de fechas no válido");
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) throw new IllegalArgumentException("El rango máximo es de 62 días");
        return deliveries.inRange(from, to, assigneeId);
    }

    @Override
    public List<UserAccount> assignees(AuthenticatedUser user) {
        Guards.require(user, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        return users.withRole(Role.LOGISTICS_OPERATOR).stream().filter(UserAccount::enabled).toList();
    }

    @Override
    public List<CommerceOrder> schedulableOrders(AuthenticatedUser user) {
        Guards.require(user, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        return orders.byStatuses(SCHEDULABLE, 200).stream()
                .filter(order -> deliveries.findByOrder(order.id()).isEmpty()).toList();
    }

    @Override
    public Delivery schedule(AuthenticatedUser user, UUID orderId, long assigneeId, Instant start, Instant end, String notes) {
        Guards.require(user, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        String cleanNotes = Guards.text(notes, 500, "notes");
        Delivery.validateWindow(start, end);
        Delivery.validateNotInPast(start, clock.instant());
        CommerceOrder order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        if (!SCHEDULABLE.contains(order.status())) {
            throw new BusinessConflictException("ORDER_NOT_SCHEDULABLE",
                    "Solo se programan pedidos CONFIRMED, PREPARING o READY_FOR_DISPATCH (estado actual: " + order.status() + ")");
        }
        if (deliveries.findByOrder(orderId).isPresent()) {
            throw new BusinessConflictException("ALREADY_SCHEDULED", "El pedido ya tiene una entrega; usa la reprogramación");
        }
        UserAccount assignee = assignee(assigneeId);
        rejectConflicts(assigneeId, start, end, null);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Delivery created = deliveries.create(
                new Delivery(0, orderId, assigneeId, assignee.displayName(), start, end, cleanNotes, 0),
                new DeliveryChange(0, user.username(), "CREATED", null, null, null, start, end, assignee.username(), cleanNotes, now));
        telemetry.deliveryScheduled(false);
        audit.record(user.username(), "DELIVERY_SCHEDULED", "order:" + orderId, start + " – " + end + " · " + assignee.username());
        return created;
    }

    @Override
    public Delivery reschedule(AuthenticatedUser user, long deliveryId, long assigneeId, Instant start, Instant end, String reason) {
        Guards.require(user, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        String cleanReason = Guards.text(reason, 500, "reason");
        if (cleanReason == null) throw new IllegalArgumentException("reason: indica el motivo de la reprogramación");
        Delivery.validateWindow(start, end);
        Delivery.validateNotInPast(start, clock.instant());
        Delivery current = deliveries.findById(deliveryId).orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada"));
        CommerceOrder order = orders.findById(current.orderId()).orElseThrow();
        if (order.status().isTerminal() || order.status() == FulfillmentStatus.IN_TRANSIT) {
            throw new BusinessConflictException("DELIVERY_LOCKED", "No se puede reprogramar un pedido " + order.status());
        }
        UserAccount assignee = assignee(assigneeId);
        rejectConflicts(assigneeId, start, end, deliveryId);
        String oldAssignee = users.findById(current.assigneeId()).map(UserAccount::username).orElse("?");
        DeliveryChange change = new DeliveryChange(deliveryId, user.username(), "RESCHEDULED", current.start(), current.end(),
                oldAssignee, start, end, assignee.username(), cleanReason, clock.instant().truncatedTo(ChronoUnit.MICROS));
        Delivery updated = deliveries.reschedule(deliveryId, current.version(), assigneeId, start, end, change)
                .orElseThrow(() -> new BusinessConflictException("CONCURRENT_UPDATE", "La entrega fue modificada por otra persona; recarga el calendario"));
        telemetry.deliveryScheduled(true);
        audit.record(user.username(), "DELIVERY_RESCHEDULED", "delivery:" + deliveryId, cleanReason);
        return updated;
    }

    @Override
    public List<DeliveryChange> history(AuthenticatedUser user, long deliveryId) {
        Guards.require(user, Role.LOGISTICS_OPERATOR, Role.ADMIN);
        deliveries.findById(deliveryId).orElseThrow(() -> new ResourceNotFoundException("Entrega no encontrada"));
        return deliveries.history(deliveryId);
    }

    private UserAccount assignee(long assigneeId) {
        return users.findById(assigneeId)
                .filter(candidate -> candidate.enabled() && candidate.hasRole(Role.LOGISTICS_OPERATOR))
                .orElseThrow(() -> new IllegalArgumentException("assigneeId: debe ser un operador logístico activo"));
    }

    private void rejectConflicts(long assigneeId, Instant start, Instant end, Long excluding) {
        List<Delivery> overlapping = deliveries.overlapping(assigneeId, start, end, excluding);
        if (!overlapping.isEmpty()) {
            telemetry.deliveryConflict();
            Delivery first = overlapping.get(0);
            throw new BusinessConflictException("SCHEDULE_CONFLICT",
                    "El responsable ya tiene una entrega de " + first.start() + " a " + first.end() + " (pedido " + first.orderId() + ")");
        }
    }
}
