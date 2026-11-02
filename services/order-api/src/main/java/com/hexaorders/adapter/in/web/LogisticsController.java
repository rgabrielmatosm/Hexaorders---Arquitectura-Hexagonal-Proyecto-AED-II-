package com.hexaorders.adapter.in.web;

import com.hexaorders.adapter.in.web.security.CurrentUser;
import com.hexaorders.application.port.in.DeliveryView;
import com.hexaorders.application.port.in.LogisticsUseCases;
import com.hexaorders.application.port.in.OperationsUseCases;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.identity.UserAccount;
import com.hexaorders.domain.logistics.Delivery;
import com.hexaorders.domain.logistics.DeliveryChange;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** LOGISTICS_OPERATOR and ADMIN: delivery calendar and logistics transitions. */
@RestController
@RequestMapping("/api/logistics")
public final class LogisticsController {
    private final LogisticsUseCases logistics;
    private final OperationsUseCases operations;

    public LogisticsController(LogisticsUseCases logistics, OperationsUseCases operations) {
        this.logistics = logistics;
        this.operations = operations;
    }

    @GetMapping("/deliveries")
    public List<DeliveryView> calendar(@RequestParam Instant from, @RequestParam Instant to, @RequestParam(required = false) Long assigneeId) {
        return logistics.calendar(CurrentUser.require(), from, to, assigneeId);
    }

    @GetMapping("/assignees")
    public List<UserAccount> assignees() { return logistics.assignees(CurrentUser.require()); }

    @GetMapping("/schedulable-orders")
    public List<CommerceOrder> schedulable() { return logistics.schedulableOrders(CurrentUser.require()); }

    @PostMapping("/deliveries")
    public ResponseEntity<Delivery> schedule(@RequestBody ScheduleRequest request) {
        Delivery created = logistics.schedule(CurrentUser.require(), request.orderId(),
                request.assigneeId() == null ? 0 : request.assigneeId(), request.start(), request.end(), request.notes());
        return ResponseEntity.created(URI.create("/api/logistics/deliveries/" + created.id())).body(created);
    }

    @PutMapping("/deliveries/{id}")
    public Delivery reschedule(@PathVariable long id, @RequestBody RescheduleRequest request) {
        return logistics.reschedule(CurrentUser.require(), id, request.assigneeId() == null ? 0 : request.assigneeId(),
                request.start(), request.end(), request.reason());
    }

    @GetMapping("/deliveries/{id}/history")
    public List<DeliveryChange> history(@PathVariable long id) { return logistics.history(CurrentUser.require(), id); }

    /** IN_TRANSIT and DELIVERED; TransitionPolicy decides which transitions the logistics role may perform. */
    @PostMapping("/orders/{id}/transitions")
    public CommerceOrder transition(@PathVariable UUID id, @RequestBody OperationsController.TransitionRequest request) {
        return operations.transition(CurrentUser.require(), id, request.status(), request.note());
    }

    public record ScheduleRequest(UUID orderId, Long assigneeId, Instant start, Instant end, String notes) {}
    public record RescheduleRequest(Long assigneeId, Instant start, Instant end, String reason) {}
}
