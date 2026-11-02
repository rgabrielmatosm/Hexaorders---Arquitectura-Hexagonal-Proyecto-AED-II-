package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.ShippingAddress;
import com.hexaorders.domain.fulfillment.StatusChange;
import com.hexaorders.domain.logistics.Delivery;
import java.util.List;
import java.util.Set;

/** An order with its history, its delivery (if scheduled) and the actions the caller may take. */
public record OrderDetails(CommerceOrder order, List<StatusChange> history, Delivery delivery, Set<FulfillmentStatus> allowedTransitions,
                           ShippingAddress shipping) {
    public OrderDetails(CommerceOrder order, List<StatusChange> history, Delivery delivery, Set<FulfillmentStatus> allowedTransitions) {
        this(order, history, delivery, allowedTransitions, null);
    }
}
