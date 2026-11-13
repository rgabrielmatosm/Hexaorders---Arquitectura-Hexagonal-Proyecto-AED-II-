package com.polymer.adapter.out.metrics;

import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.domain.OrderStatus;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.TransitionPolicy;
import com.polymer.domain.incident.IncidentCategory;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;

/**
 * Platform metrics (prefix polymer_). Every label has a small, fixed set of values and all
 * series are registered at startup, so dashboards never query a metric that does not exist yet.
 */
public final class MicrometerBusinessTelemetry implements BusinessTelemetry {
    static final List<String> CHECKOUT_FAILURE_REASONS = List.of("invalid", "out_of_stock", "risk_unavailable");

    private final MeterRegistry registry;

    public MicrometerBusinessTelemetry(MeterRegistry registry) {
        this.registry = registry;
        for (OrderStatus decision : OrderStatus.values()) placed(decision);
        CHECKOUT_FAILURE_REASONS.forEach(this::checkoutCounter);
        for (FulfillmentStatus from : FulfillmentStatus.values()) {
            for (FulfillmentStatus to : FulfillmentStatus.values()) {
                if (TransitionPolicy.exists(from, to)) transitionCounter(from, to);
            }
        }
        deliveryCounter("scheduled");
        deliveryCounter("rescheduled");
        conflicts();
        for (IncidentCategory category : IncidentCategory.values()) incidentCounter(category);
        for (String method : PAYMENT_METHODS) for (String outcome : PAYMENT_OUTCOMES) paymentCounter(method, outcome);
    }

    static final List<String> PAYMENT_METHODS = List.of("CARD", "YAPE", "PLIN");
    static final List<String> PAYMENT_OUTCOMES = List.of("started", "paid", "declined", "expired");

    private Counter paymentCounter(String method, String outcome) {
        return Counter.builder("polymer_payments").description("Pagos en línea por método y resultado")
                .tag("method", method).tag("outcome", outcome).register(registry);
    }

    @Override
    public void payment(String method, String outcome) {
        if (PAYMENT_METHODS.contains(method) && PAYMENT_OUTCOMES.contains(outcome)) paymentCounter(method, outcome).increment();
    }

    private Counter placed(OrderStatus decision) {
        return Counter.builder("polymer_shop_orders_placed").description("Pedidos creados desde el portal por decisión de riesgo")
                .tag("risk_decision", decision.name()).register(registry);
    }

    private Counter checkoutCounter(String reason) {
        return Counter.builder("polymer_checkout_failures").description("Checkouts rechazados por motivo")
                .tag("reason", reason).register(registry);
    }

    private Counter transitionCounter(FulfillmentStatus from, FulfillmentStatus to) {
        return Counter.builder("polymer_order_transitions").description("Transiciones del ciclo de vida de pedidos")
                .tag("from", from.name()).tag("to", to.name()).register(registry);
    }

    private Counter deliveryCounter(String type) {
        return Counter.builder("polymer_deliveries").description("Entregas programadas y reprogramadas")
                .tag("type", type).register(registry);
    }

    private Counter conflicts() {
        return Counter.builder("polymer_delivery_conflicts").description("Intentos de programación rechazados por solapamiento")
                .register(registry);
    }

    private Counter incidentCounter(IncidentCategory category) {
        return Counter.builder("polymer_incidents_reported").description("Incidencias registradas por categoría")
                .tag("category", category.name()).register(registry);
    }

    @Override
    public void shopOrderPlaced(OrderStatus riskDecision) { placed(riskDecision).increment(); }

    @Override
    public void checkoutFailed(String reason) {
        checkoutCounter(CHECKOUT_FAILURE_REASONS.contains(reason) ? reason : "invalid").increment();
    }

    @Override
    public void transition(FulfillmentStatus from, FulfillmentStatus to) { transitionCounter(from, to).increment(); }

    @Override
    public void deliveryScheduled(boolean rescheduled) { deliveryCounter(rescheduled ? "rescheduled" : "scheduled").increment(); }

    @Override
    public void deliveryConflict() { conflicts().increment(); }

    @Override
    public void incidentReported(IncidentCategory category) { incidentCounter(category).increment(); }
}
