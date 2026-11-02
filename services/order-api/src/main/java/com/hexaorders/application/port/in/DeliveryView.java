package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.logistics.Delivery;
import java.math.BigDecimal;

/** destination: the city of the shipping address (null for orders placed before v4). */
public record DeliveryView(Delivery delivery, FulfillmentStatus orderStatus, String customerId, BigDecimal amount, String destination) {
    public DeliveryView(Delivery delivery, FulfillmentStatus orderStatus, String customerId, BigDecimal amount) {
        this(delivery, orderStatus, customerId, amount, null);
    }
}
