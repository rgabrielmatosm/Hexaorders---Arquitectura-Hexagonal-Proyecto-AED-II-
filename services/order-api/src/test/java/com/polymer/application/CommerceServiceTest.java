package com.polymer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.exception.OutOfStockException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.exception.RiskUnavailableException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CatalogRepository;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.DeliveryRepository;
import com.polymer.application.port.out.NewShopOrder;
import com.polymer.application.port.out.OrderTelemetry;
import com.polymer.application.port.out.RiskAssessmentPort;
import com.polymer.application.service.CommerceService;
import com.polymer.domain.OrderStatus;
import com.polymer.domain.catalog.Product;
import com.polymer.domain.catalog.ProductDetails;
import com.polymer.domain.fulfillment.CartItem;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.identity.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CommerceServiceTest {
    private final CatalogRepository catalog = mock(CatalogRepository.class);
    private final CommerceOrderRepository orders = mock(CommerceOrderRepository.class);
    private final DeliveryRepository deliveries = mock(DeliveryRepository.class);
    private final RiskAssessmentPort risk = mock(RiskAssessmentPort.class);
    private final OrderTelemetry orderTelemetry = mock(OrderTelemetry.class);
    private final BusinessTelemetry telemetry = mock(BusinessTelemetry.class);
    private final AuditLog audit = mock(AuditLog.class);
    private final CommerceService service = new CommerceService(catalog, orders, deliveries, risk, orderTelemetry, telemetry, audit,
            Clock.fixed(Instant.parse("2026-11-01T10:00:00Z"), ZoneOffset.UTC));
    private final AuthenticatedUser customer = new AuthenticatedUser(42, "cliente", EnumSet.of(Role.CUSTOMER));
    private final Product laptop = new Product(1, new ProductDetails("TEC-LAPTOP", "Portátil", "", 1, new BigDecimal("1299.00"), true, 260), "Tecnología", 3);

    @BeforeEach
    void catalogHasLaptop() {
        when(catalog.productsByIds(anyCollection())).thenReturn(Map.of(1L, laptop));
        when(orders.findByIdempotencyKey(any())).thenReturn(Optional.empty());
    }

    private static CommerceOrder stored(UUID id, FulfillmentStatus status) {
        return new CommerceOrder(id, "cliente", new BigDecimal("1299.00"), "USD", 75, OrderStatus.REVIEW, status, "SHOP", 0,
                Instant.parse("2026-11-01T10:00:00Z"), List.of());
    }

    @Test
    void pricesOnTheServerAsksPythonForRiskBeforePersistingAndRecordsHistory() {
        when(risk.assess(any())).thenReturn(75);
        when(orders.placeOrder(any())).thenAnswer(call -> stored(((NewShopOrder) call.getArgument(0)).order().id(), FulfillmentStatus.RISK_REVIEW));

        var result = service.placeOrder(customer, List.of(new CartItem(1, 1)), "key-0000001");

        assertThat(result.created()).isTrue();
        var captor = ArgumentCaptor.forClass(NewShopOrder.class);
        var calls = inOrder(risk, orders, orderTelemetry);
        calls.verify(risk).assess(any());
        calls.verify(orders).placeOrder(captor.capture());
        calls.verify(orderTelemetry).orderCreated(OrderStatus.REVIEW);
        NewShopOrder persisted = captor.getValue();
        assertThat(persisted.order().amount()).isEqualByComparingTo("1299.00");
        assertThat(persisted.status()).isEqualTo(FulfillmentStatus.RISK_REVIEW);
        assertThat(persisted.history()).extracting(h -> h.to()).containsExactly(FulfillmentStatus.CREATED, FulfillmentStatus.RISK_REVIEW);
        assertThat(persisted.userId()).isEqualTo(42);
        verify(telemetry).shopOrderPlaced(OrderStatus.REVIEW);
    }

    @Test
    void riskUnavailableNeverPersistsAndUsesTheOriginalFailureMetric() {
        when(risk.assess(any())).thenThrow(new RiskUnavailableException("timeout"));
        assertThatThrownBy(() -> service.placeOrder(customer, List.of(new CartItem(1, 1)), "key-0000002"))
                .isInstanceOf(RiskUnavailableException.class);
        verify(orders, never()).placeOrder(any());
        verify(orderTelemetry).riskFailure();
        verify(telemetry).checkoutFailed("risk_unavailable");
    }

    @Test
    void insufficientStockIsRejectedBeforeCallingTheRiskEngine() {
        assertThatThrownBy(() -> service.placeOrder(customer, List.of(new CartItem(1, 4)), "key-0000003"))
                .isInstanceOf(OutOfStockException.class);
        verify(risk, never()).assess(any());
        verify(telemetry).checkoutFailed("out_of_stock");
    }

    @Test
    void sameIdempotencyKeyReturnsTheOriginalOrderWithoutCreatingAnother() {
        UUID id = UUID.randomUUID();
        when(orders.findByIdempotencyKey("key-0000004")).thenReturn(Optional.of(stored(id, FulfillmentStatus.RISK_REVIEW)));
        when(orders.ownerOf(id)).thenReturn(Optional.of(42L));
        var replay = service.placeOrder(customer, List.of(new CartItem(1, 1)), "key-0000004");
        assertThat(replay.created()).isFalse();
        assertThat(replay.order().id()).isEqualTo(id);
        verify(risk, never()).assess(any());
        verify(orders, never()).placeOrder(any());
    }

    @Test
    void idempotencyKeyOfAnotherCustomerIsAConflict() {
        UUID id = UUID.randomUUID();
        when(orders.findByIdempotencyKey("key-0000005")).thenReturn(Optional.of(stored(id, FulfillmentStatus.RISK_REVIEW)));
        when(orders.ownerOf(id)).thenReturn(Optional.of(7L));
        assertThatThrownBy(() -> service.placeOrder(customer, List.of(new CartItem(1, 1)), "key-0000005"))
                .isInstanceOf(BusinessConflictException.class);
    }

    @Test
    void customersCannotReadOrdersOfOtherCustomers() {
        UUID id = UUID.randomUUID();
        when(orders.ownerOf(id)).thenReturn(Optional.of(7L));
        assertThatThrownBy(() -> service.myOrder(customer, id)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.cancelMyOrder(customer, id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void onlyCustomersCanCheckOutAndKeysAreValidated() {
        var admin = new AuthenticatedUser(1, "admin", EnumSet.of(Role.ADMIN));
        assertThatThrownBy(() -> service.placeOrder(admin, List.of(new CartItem(1, 1)), "key-0000006"))
                .isInstanceOf(ForbiddenOperationException.class);
        assertThatThrownBy(() -> service.placeOrder(customer, List.of(new CartItem(1, 1)), "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
