package com.hexaorders.application.service;

import com.hexaorders.application.exception.OrderNotFoundException;
import com.hexaorders.application.exception.RiskUnavailableException;
import com.hexaorders.application.port.in.OrderUseCases;
import com.hexaorders.application.port.out.OrderRepository;
import com.hexaorders.application.port.out.OrderTelemetry;
import com.hexaorders.application.port.out.RiskAssessmentPort;
import com.hexaorders.domain.Order;
import com.hexaorders.domain.OrderDraft;
import com.hexaorders.domain.OrderStatistics;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Plain Java orchestration: risk first; one atomic INSERT afterwards. */
public final class OrderService implements OrderUseCases {
    private final OrderRepository repository;
    private final RiskAssessmentPort riskAssessment;
    private final OrderTelemetry telemetry;
    private final Clock clock;

    public OrderService(OrderRepository repository, RiskAssessmentPort riskAssessment,
                        OrderTelemetry telemetry, Clock clock) {
        this.repository = repository;
        this.riskAssessment = riskAssessment;
        this.telemetry = telemetry;
        this.clock = clock;
    }

    @Override
    public Order create(OrderDraft draft) {
        // Contract of the original API (v1): only USD. The store (CommerceService) sells in PEN.
        if (!"USD".equals(draft.currency())) throw new IllegalArgumentException("currency: la única moneda admitida es USD");
        int score;
        try {
            score = riskAssessment.assess(draft);
            if (score < 0 || score > 100) {
                throw new RiskUnavailableException("El motor de riesgo devolvió un puntaje inválido");
            }
        } catch (RiskUnavailableException error) {
            telemetry.riskFailure();
            throw error;
        }
        // Microsecond precision: the instant returned to the client is exactly the one that can be
        // persisted (PostgreSQL TIMESTAMPTZ), so POST and a later GET return the same createdAt.
        Order order = Order.create(draft, score, UUID.randomUUID(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        repository.save(order);
        telemetry.orderCreated(order.status());
        return order;
    }

    @Override
    public Order get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Override
    public List<Order> latest() {
        return repository.latest(50);
    }

    @Override
    public OrderStatistics statistics() {
        return repository.statistics();
    }
}
