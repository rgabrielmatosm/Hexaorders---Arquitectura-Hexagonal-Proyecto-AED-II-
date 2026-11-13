package com.polymer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.exception.MonitoringUnavailableException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CatalogRepository;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.ContactMessageRepository;
import com.polymer.application.port.out.DeliveryRepository;
import com.polymer.application.port.out.MonitoringPort;
import com.polymer.application.port.out.NewShopOrder;
import com.polymer.application.port.out.OrderTelemetry;
import com.polymer.application.port.out.RiskAssessmentPort;
import com.polymer.application.service.CommerceService;
import com.polymer.application.service.ContactService;
import com.polymer.application.service.MonitoringService;
import com.polymer.domain.OrderStatus;
import com.polymer.domain.catalog.Product;
import com.polymer.domain.catalog.ProductDetails;
import com.polymer.domain.contact.ContactMessage;
import com.polymer.domain.fulfillment.CartItem;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.ShippingAddress;
import com.polymer.domain.identity.Role;
import com.polymer.domain.monitoring.AlertRule;
import com.polymer.domain.monitoring.Indicator;
import com.polymer.domain.monitoring.ServiceTarget;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WebPlatformServicesTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T10:00:00Z"), ZoneOffset.UTC);
    private final AuthenticatedUser admin = new AuthenticatedUser(1, "admin", EnumSet.of(Role.ADMIN));
    private final AuthenticatedUser viewer = new AuthenticatedUser(2, "observador", EnumSet.of(Role.OBSERVABILITY_VIEWER));
    private final AuthenticatedUser customer = new AuthenticatedUser(42, "cliente", EnumSet.of(Role.CUSTOMER));

    @Test
    void contactMessagesArePersistedAndAuditedWithoutTheirContent() {
        var repo = mock(ContactMessageRepository.class);
        var audit = mock(AuditLog.class);
        when(repo.save(any())).thenAnswer(call -> {
            ContactMessage m = call.getArgument(0);
            return new ContactMessage(7, m.name(), m.email(), m.topic(), m.message(), m.createdAt());
        });
        var saved = new ContactService(repo, audit, CLOCK).send("Ana", "ana@example.com", "demo", "Quiero una demostración guiada.");
        assertThat(saved.id()).isEqualTo(7);
        assertThat(saved.topic()).isEqualTo("DEMO");
        verify(audit).record(eq("public"), eq("CONTACT_MESSAGE_RECEIVED"), eq("contact:7"), contains("DEMO"));
        verify(audit, never()).record(any(), any(), any(), contains("ana@example.com"));
    }

    @Test
    void onlyAdminsReadContactMessages() {
        var repo = mock(ContactMessageRepository.class);
        var service = new ContactService(repo, mock(AuditLog.class), CLOCK);
        assertThatThrownBy(() -> service.recent(customer, 10)).isInstanceOf(ForbiddenOperationException.class);
        service.recent(admin, 5000);
        verify(repo).recent(200);
    }

    @Test
    void monitoringSnapshotCombinesTargetsAlertsAndIndicators() {
        var port = mock(MonitoringPort.class);
        when(port.targets()).thenReturn(List.of(new ServiceTarget("order-api", "order-api:9090", "up", null, 0.01, null)));
        when(port.alerts()).thenReturn(List.of(new AlertRule("polymer", "PolymerServiceDown", "inactive", "critical", "", null)));
        when(port.indicators()).thenReturn(List.of(new Indicator("orders_created", "Pedidos", "", 3.0)));
        var snapshot = new MonitoringService(port, CLOCK).snapshot(viewer);
        assertThat(snapshot.available()).isTrue();
        assertThat(snapshot.targets()).hasSize(1);
        assertThat(snapshot.indicators().get(0).value()).isEqualTo(3.0);
    }

    @Test
    void monitoringDegradesWhenPrometheusIsDownAndRequiresTheViewerRole() {
        var port = mock(MonitoringPort.class);
        when(port.targets()).thenThrow(new MonitoringUnavailableException("down", null));
        var service = new MonitoringService(port, CLOCK);
        assertThat(service.snapshot(admin).available()).isFalse();
        assertThatThrownBy(() -> service.snapshot(customer)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void checkoutStoresTheShippingAddressAndTheOwnerCanReadItBack() {
        var catalog = mock(CatalogRepository.class);
        var orders = mock(CommerceOrderRepository.class);
        var risk = mock(RiskAssessmentPort.class);
        var service = new CommerceService(catalog, orders, mock(DeliveryRepository.class), risk, mock(OrderTelemetry.class),
                mock(BusinessTelemetry.class), mock(AuditLog.class), CLOCK);
        var laptop = new Product(1, new ProductDetails("TEC-LAPTOP", "Portátil", "", 1, new BigDecimal("100.00"), true, 260), "Tecnología", 5);
        when(catalog.productsByIds(anyCollection())).thenReturn(Map.of(1L, laptop));
        when(orders.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(risk.assess(any())).thenReturn(10);
        UUID[] id = new UUID[1];
        when(orders.placeOrder(any())).thenAnswer(call -> {
            NewShopOrder o = call.getArgument(0);
            id[0] = o.order().id();
            return new CommerceOrder(id[0], "cliente", new BigDecimal("100.00"), "USD", 10, OrderStatus.APPROVED,
                    FulfillmentStatus.RISK_APPROVED, "SHOP", 0, Instant.parse("2026-11-02T10:00:00Z"), List.of());
        });
        var shipping = new ShippingAddress("Ana Pérez", "Av. Arequipa 1234", "Lima", "+51 999 888 777", null);

        service.placeOrder(customer, List.of(new CartItem(1, 1)), "key-ship-0001", shipping);

        var captor = ArgumentCaptor.forClass(NewShopOrder.class);
        verify(orders).placeOrder(captor.capture());
        assertThat(captor.getValue().shipping()).isEqualTo(shipping);

        when(orders.ownerOf(id[0])).thenReturn(Optional.of(42L));
        when(orders.findById(id[0])).thenReturn(Optional.of(new CommerceOrder(id[0], "cliente", new BigDecimal("100.00"), "USD", 10,
                OrderStatus.APPROVED, FulfillmentStatus.RISK_APPROVED, "SHOP", 0, Instant.parse("2026-11-02T10:00:00Z"), List.of())));
        when(orders.shippingOf(id[0])).thenReturn(Optional.of(shipping));
        assertThat(service.myOrder(customer, id[0]).shipping().city()).isEqualTo("Lima");
    }
}
