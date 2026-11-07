package com.hexaorders.application.port.out;

import com.hexaorders.domain.Order;
import com.hexaorders.domain.fulfillment.CheckoutSummary;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.OrderLine;
import com.hexaorders.domain.fulfillment.ShippingAddress;
import com.hexaorders.domain.fulfillment.StatusChange;
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
