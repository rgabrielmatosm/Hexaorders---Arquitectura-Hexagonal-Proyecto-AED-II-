package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.logistics.Delivery;
import java.math.BigDecimal;

public record DeliveryView(Delivery delivery, FulfillmentStatus orderStatus, String customerId, BigDecimal amount) {}
