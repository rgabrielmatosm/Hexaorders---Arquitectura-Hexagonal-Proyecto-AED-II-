package com.hexaorders.adapter.in.web;

import com.hexaorders.application.port.in.OrderUseCases;
import com.hexaorders.domain.Order;
import com.hexaorders.domain.OrderDraft;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public final class OrderController {
    private final OrderUseCases orders;

    public OrderController(OrderUseCases orders) { this.orders = orders; }

    @PostMapping
    public ResponseEntity<Order> create(@RequestBody CreateOrderRequest request) {
        String currency = request.currency() == null ? "USD" : request.currency();
        Order created = orders.create(new OrderDraft(request.customerId(), request.amount(), currency));
        return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable UUID id) { return orders.get(id); }

    @GetMapping
    public List<Order> latest() { return orders.latest(); }

    public record CreateOrderRequest(String customerId, BigDecimal amount, String currency) {}
}
