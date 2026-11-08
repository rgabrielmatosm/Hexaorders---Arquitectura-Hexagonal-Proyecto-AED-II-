package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.OperationsUseCases;
import com.polymer.application.port.in.OrderDetails;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ORDER_OPERATOR and ADMIN: order queue, details and lifecycle transitions. */
@RestController
@RequestMapping("/api/operations")
public final class OperationsController {
    private final OperationsUseCases operations;

    public OperationsController(OperationsUseCases operations) { this.operations = operations; }

    @GetMapping("/orders")
    public List<CommerceOrder> queue(@RequestParam(required = false) String status) {
        return operations.queue(CurrentUser.require(), parseStatuses(status));
    }

    @GetMapping("/orders/{id}")
    public OrderDetails details(@PathVariable UUID id) { return operations.details(CurrentUser.require(), id); }

    @PostMapping("/orders/{id}/transitions")
    public CommerceOrder transition(@PathVariable UUID id, @RequestBody TransitionRequest request) {
        return operations.transition(CurrentUser.require(), id, request.status(), request.note());
    }

    static Set<FulfillmentStatus> parseStatuses(String csv) {
        Set<FulfillmentStatus> statuses = EnumSet.noneOf(FulfillmentStatus.class);
        if (csv != null && !csv.isBlank()) {
            Arrays.stream(csv.split(",")).map(String::strip).filter(s -> !s.isEmpty()).map(FulfillmentStatus::valueOf).forEach(statuses::add);
        }
        return statuses;
    }

    public record TransitionRequest(FulfillmentStatus status, String note) {}
}
