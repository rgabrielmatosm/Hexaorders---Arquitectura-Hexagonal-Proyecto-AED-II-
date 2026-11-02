package com.hexaorders.application.port.out;

import com.hexaorders.domain.Order;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.OrderLine;
import com.hexaorders.domain.fulfillment.ShippingAddress;
import com.hexaorders.domain.fulfillment.StatusChange;
import java.util.List;

/** Everything persisted atomically when a customer places an order. */
public record NewShopOrder(Order order, long userId, FulfillmentStatus status, List<OrderLine> lines,
                           List<StatusChange> history, String idempotencyKey, ShippingAddress shipping) {
    public NewShopOrder(Order order, long userId, FulfillmentStatus status, List<OrderLine> lines,
                        List<StatusChange> history, String idempotencyKey) {
        this(order, userId, status, lines, history, idempotencyKey, null);
    }

    public NewShopOrder {
        lines = List.copyOf(lines);
        history = List.copyOf(history);
    }
}
