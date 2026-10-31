package com.hexaorders.application.port.out;

import com.hexaorders.domain.OrderStatus;

public interface OrderTelemetry {
    void orderCreated(OrderStatus status);
    void riskFailure();
}
