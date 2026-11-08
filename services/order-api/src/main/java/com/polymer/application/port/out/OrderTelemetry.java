package com.polymer.application.port.out;

import com.polymer.domain.OrderStatus;

public interface OrderTelemetry {
    void orderCreated(OrderStatus status);
    void riskFailure();
}
