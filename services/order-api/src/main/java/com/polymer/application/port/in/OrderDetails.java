package com.polymer.application.port.in;

import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.OrderCheckout;
import com.polymer.domain.fulfillment.ShippingAddress;
import com.polymer.domain.fulfillment.StatusChange;
import com.polymer.domain.logistics.Delivery;
import java.util.List;
import java.util.Set;

/**
 * An order with its history, its delivery (if scheduled) and the actions the caller may take.
 * v6: checkout holds delivery method, payment method and totals (null for older orders).
 */
public record OrderDetails(CommerceOrder order, List<StatusChange> history, Delivery delivery, Set<FulfillmentStatus> allowedTransitions,
                           ShippingAddress shipping, OrderCheckout checkout) {
    public OrderDetails(CommerceOrder order, List<StatusChange> history, Delivery delivery, Set<FulfillmentStatus> allowedTransitions) {
        this(order, history, delivery, allowedTransitions, null, null);
    }

    public OrderDetails(CommerceOrder order, List<StatusChange> history, Delivery delivery, Set<FulfillmentStatus> allowedTransitions,
                        ShippingAddress shipping) {
        this(order, history, delivery, allowedTransitions, shipping, null);
    }
}
