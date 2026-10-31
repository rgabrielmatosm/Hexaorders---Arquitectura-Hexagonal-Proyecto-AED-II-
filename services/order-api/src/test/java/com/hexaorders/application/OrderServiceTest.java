package com.hexaorders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hexaorders.application.exception.OrderNotFoundException;
import com.hexaorders.application.exception.PersistenceUnavailableException;
import com.hexaorders.application.exception.RiskUnavailableException;
import com.hexaorders.application.port.out.OrderRepository;
import com.hexaorders.application.port.out.OrderTelemetry;
import com.hexaorders.application.port.out.RiskAssessmentPort;
import com.hexaorders.application.service.OrderService;
import com.hexaorders.domain.Order;
import com.hexaorders.domain.OrderDraft;
import com.hexaorders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OrderServiceTest {
    private final OrderRepository repository = mock(OrderRepository.class);
    private final RiskAssessmentPort risk = mock(RiskAssessmentPort.class);
    private final OrderTelemetry telemetry = mock(OrderTelemetry.class);
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final OrderService service = new OrderService(repository, risk, telemetry, Clock.fixed(now, ZoneOffset.UTC));
    private final OrderDraft draft = new OrderDraft("cliente_1", new BigDecimal("125.50"), "USD");

    @Test
    void assessesRiskBeforePersistenceAndCountsOnlyPersistedOrders() {
        when(risk.assess(draft)).thenReturn(70);
        Order order = service.create(draft);
        assertThat(order.status()).isEqualTo(OrderStatus.REVIEW);
        assertThat(order.createdAt()).isEqualTo(now);
        InOrder orderOfCalls = inOrder(risk, repository, telemetry);
        orderOfCalls.verify(risk).assess(draft);
        orderOfCalls.verify(repository).save(order);
        orderOfCalls.verify(telemetry).orderCreated(OrderStatus.REVIEW);
    }

    @Test
    void neverPersistsWhenRiskEngineIsUnavailable() {
        when(risk.assess(draft)).thenThrow(new RiskUnavailableException("timeout"));
        assertThatThrownBy(() -> service.create(draft)).isInstanceOf(RiskUnavailableException.class);
        verifyNoInteractions(repository);
        verify(telemetry).riskFailure();
    }

    @Test
    void guardsAgainstInvalidAdapterScores() {
        when(risk.assess(draft)).thenReturn(101);
        assertThatThrownBy(() -> service.create(draft)).isInstanceOf(RiskUnavailableException.class);
        verifyNoInteractions(repository);
        verify(telemetry).riskFailure();
    }

    @Test
    void neverCountsUncommittedOrderAsCreated() {
        when(risk.assess(draft)).thenReturn(20);
        doThrow(new PersistenceUnavailableException(new RuntimeException("db offline"))).when(repository).save(any());
        assertThatThrownBy(() -> service.create(draft)).isInstanceOf(PersistenceUnavailableException.class);
        verifyNoInteractions(telemetry);
    }

    @Test
    void missingOrderHasAnExplicitApplicationError() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void limitsListToFiftyOrders() {
        service.latest();
        verify(repository).latest(50);
    }
}
