package com.polymer.adapter.out.metrics;

import com.polymer.application.port.out.OrderTelemetry;
import com.polymer.domain.OrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;

public final class MicrometerOrderTelemetry implements OrderTelemetry {
    private final Map<OrderStatus, Counter> created = new EnumMap<>(OrderStatus.class);
    private final Counter riskFailures;

    public MicrometerOrderTelemetry(MeterRegistry registry) {
        for (OrderStatus status : OrderStatus.values()) {
            created.put(status, Counter.builder("orders_created").description("Pedidos persistidos")
                    .tag("status", status.name()).register(registry));
        }
        riskFailures = Counter.builder("orders_risk_failures").description("Fallos del motor de riesgo")
                .register(registry);
    }

    @Override
    public void orderCreated(OrderStatus status) { created.get(status).increment(); }

    @Override
    public void riskFailure() { riskFailures.increment(); }
}
