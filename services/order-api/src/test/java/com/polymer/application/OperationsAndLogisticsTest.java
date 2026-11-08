package com.polymer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.DeliveryRepository;
import com.polymer.application.port.out.IncidentRepository;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.service.LogisticsService;
import com.polymer.application.service.OperationsService;
import com.polymer.domain.OrderStatus;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.TransitionNotAllowedException;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.logistics.Delivery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationsAndLogisticsTest {
    private final CommerceOrderRepository orders = mock(CommerceOrderRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BusinessTelemetry telemetry = mock(BusinessTelemetry.class);
    private final AuditLog audit = mock(AuditLog.class);
    private final Instant now = Instant.parse("2026-11-01T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final OperationsService operations = new OperationsService(orders, deliveries, incidents, telemetry, audit, clock);
    private final LogisticsService logistics = new LogisticsService(deliveries, orders, users, telemetry, audit, clock);
    private final AuthenticatedUser operator = new AuthenticatedUser(2, "operador", EnumSet.of(Role.ORDER_OPERATOR));
    private final AuthenticatedUser courier = new AuthenticatedUser(3, "logistica", EnumSet.of(Role.LOGISTICS_OPERATOR));
    private final UUID id = UUID.randomUUID();

    private CommerceOrder order(FulfillmentStatus status, String channel) {
        return new CommerceOrder(id, "cliente", new BigDecimal("100.00"), "USD", 10, OrderStatus.APPROVED, status, channel, 4, now, List.of());
    }

    @Test
    void operatorConfirmsWithOptimisticLockAndHistory() {
        when(orders.findById(id)).thenReturn(Optional.of(order(FulfillmentStatus.RISK_APPROVED, "SHOP")), Optional.of(order(FulfillmentStatus.CONFIRMED, "SHOP")));
        when(orders.transition(eq(id), eq(4), eq(FulfillmentStatus.RISK_APPROVED), any(), eq(false))).thenReturn(true);
        assertThat(operations.transition(operator, id, FulfillmentStatus.CONFIRMED, "ok").status()).isEqualTo(FulfillmentStatus.CONFIRMED);
        verify(telemetry).transition(FulfillmentStatus.RISK_APPROVED, FulfillmentStatus.CONFIRMED);
    }

    @Test
    void repeatingTheSameTransitionIsIdempotent() {
        when(orders.findById(id)).thenReturn(Optional.of(order(FulfillmentStatus.CONFIRMED, "SHOP")));
        assertThat(operations.transition(operator, id, FulfillmentStatus.CONFIRMED, null).status()).isEqualTo(FulfillmentStatus.CONFIRMED);
        verify(orders, never()).transition(any(), anyInt(), any(), any(), anyBoolean());
    }

    @Test
    void concurrentModificationIsReportedAsConflict() {
        when(orders.findById(id)).thenReturn(Optional.of(order(FulfillmentStatus.RISK_APPROVED, "SHOP")));
        when(orders.transition(any(), anyInt(), any(), any(), anyBoolean())).thenReturn(false);
        assertThatThrownBy(() -> operations.transition(operator, id, FulfillmentStatus.CONFIRMED, null))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void cancellingAShopOrderReleasesStockInTheSameTransaction() {
        when(orders.findById(id)).thenReturn(Optional.of(order(FulfillmentStatus.CONFIRMED, "SHOP")));
        when(orders.transition(eq(id), eq(4), eq(FulfillmentStatus.CONFIRMED), any(), eq(true))).thenReturn(true);
        operations.transition(operator, id, FulfillmentStatus.CANCELLED, "cliente lo pidió");
        verify(orders).transition(eq(id), eq(4), eq(FulfillmentStatus.CONFIRMED), any(), eq(true));
    }

    @Test
    void operatorCannotDispatchAndDispatchRequiresAScheduledDelivery() {
        when(orders.findById(id)).thenReturn(Optional.of(order(FulfillmentStatus.READY_FOR_DISPATCH, "SHOP")));
        assertThatThrownBy(() -> operations.transition(operator, id, FulfillmentStatus.IN_TRANSIT, null))
                .isInstanceOf(TransitionNotAllowedException.class);
        when(deliveries.findByOrder(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> operations.transition(courier, id, FulfillmentStatus.IN_TRANSIT, null))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("Programa la entrega");
    }

    @Test
    void overlappingDeliveryForTheSameOperatorIsRejected() {
        Instant start = now.plusSeconds(86400);
        when(orders.findById(id)).thenReturn(Optional.of(order(FulfillmentStatus.CONFIRMED, "SHOP")));
        when(deliveries.findByOrder(id)).thenReturn(Optional.empty());
        when(users.findById(3)).thenReturn(Optional.of(new UserAccount(3, "logistica", "Ana", "a@b.co", EnumSet.of(Role.LOGISTICS_OPERATOR), true, now)));
        when(deliveries.overlapping(eq(3L), any(), any(), eq(null))).thenReturn(List.of(
                new Delivery(9, UUID.randomUUID(), 3, "Ana", start.minusSeconds(1800), start.plusSeconds(1800), null, 0)));
        assertThatThrownBy(() -> logistics.schedule(courier, id, 3, start, start.plusSeconds(3600), null))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("ya tiene una entrega");
        verify(telemetry).deliveryConflict();
        verify(deliveries, never()).create(any(), any());
    }

    @Test
    void onlyConfirmedOrLaterOrdersCanBeScheduledAndNeverInThePast() {
        when(orders.findById(id)).thenReturn(Optional.of(order(FulfillmentStatus.RISK_REVIEW, "SHOP")));
        assertThatThrownBy(() -> logistics.schedule(courier, id, 3, now.plusSeconds(3600), now.plusSeconds(7200), null))
                .isInstanceOf(BusinessConflictException.class);
        assertThatThrownBy(() -> logistics.schedule(courier, id, 3, now.minusSeconds(86400), now.minusSeconds(80000), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(deliveries, never()).overlapping(anyLong(), any(), any(), any());
    }
}
