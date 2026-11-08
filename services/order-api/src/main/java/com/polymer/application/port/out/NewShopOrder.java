package com.polymer.application.port.out;

import com.polymer.domain.Order;
import com.polymer.domain.fulfillment.CheckoutSummary;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.OrderLine;
import com.polymer.domain.fulfillment.ShippingAddress;
import com.polymer.domain.fulfillment.StatusChange;
import java.util.List;

/** Everything persisted atomically when a customer places an order. v6: delivery, payment and totals. */
public record NewShopOrder(Order order, long userId, FulfillmentStatus status, List<OrderLine> lines,
                           List<StatusChange> history, String idempotencyKey, ShippingAddress shipping, CheckoutSummary checkout) {
    public NewShopOrder(Order order, long userId, FulfillmentStatus status, List<OrderLine> lines,
                        List<StatusChange> history, String idempotencyKey) {
        this(order, userId, status, lines, history, idempotencyKey, null, null);
    }

    public NewShopOrder(Order order, long userId, FulfillmentStatus status, List<OrderLine> lines,
                        List<StatusChange> history, String idempotencyKey, ShippingAddress shipping) {
        this(order, userId, status, lines, history, idempotencyKey, shipping, null);
    }

    public NewShopOrder {
        lines = List.copyOf(lines);
        history = List.copyOf(history);
    }
}
