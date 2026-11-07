package com.hexaorders.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaorders.application.exception.BusinessConflictException;
import com.hexaorders.application.exception.ForbiddenOperationException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.BusinessTelemetry;
import com.hexaorders.application.port.out.CartRepository;
import com.hexaorders.application.port.out.CatalogCache;
import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.application.port.out.CommerceOrderRepository;
import com.hexaorders.application.port.out.DeliveryRepository;
import com.hexaorders.application.port.out.NewShopOrder;
import com.hexaorders.application.port.out.OrderTelemetry;
import com.hexaorders.application.port.out.PromotionRepository;
import com.hexaorders.application.port.out.RiskAssessmentPort;
import com.hexaorders.application.service.CartService;
import com.hexaorders.application.service.CatalogService;
import com.hexaorders.application.service.CommerceService;
import com.hexaorders.application.service.PricingService;
import com.hexaorders.application.service.PromotionService;
import com.hexaorders.domain.OrderDraft;
import com.hexaorders.domain.OrderStatus;
import com.hexaorders.domain.catalog.CatalogQuery;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.CommercialStatus;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.ProductDetails;
import com.hexaorders.domain.catalog.Promotion;
import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.DeliveryMethod;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.PaymentMethod;
import com.hexaorders.domain.fulfillment.ShippingAddress;
import com.hexaorders.domain.identity.Role;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** v6 use cases: saved cart, checkout with delivery and payment, promotions and the store search. */
class TechStoreServicesTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);
    private final CatalogRepository catalog = mock(CatalogRepository.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final PricingService pricing = new PricingService(promotions, clock);
    private final AuthenticatedUser customer = new AuthenticatedUser(42, "cliente", EnumSet.of(Role.CUSTOMER));
    private final AuthenticatedUser admin = new AuthenticatedUser(1, "admin", EnumSet.of(Role.ADMIN));
    private final Product mouse = new Product(7, new ProductDetails("PER-02-01", "Mouse Logitech MX Master 3S", "", 51,
            new BigDecimal("449.00"), true, 45, "Logitech", "MX Master 3S", "", List.of(), Map.of(), null, CommercialStatus.STANDARD),
            "Mouse", 3, 50L, "Periféricos", null, null);
    private final Promotion peripherals = new Promotion(1, "campana-perifericos", "Campaña de periféricos", "", 10,
            Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-12-01T00:00:00Z"), true, Set.of(50L), Set.of());

    /** In-memory saved cart. */
    static final class MemoryCart implements CartRepository {
        final Map<Long, Integer> lines = new java.util.LinkedHashMap<>();
        @Override public List<CartItem> items(long userId) {
            List<CartItem> items = new ArrayList<>();
            lines.forEach((id, qty) -> items.add(new CartItem(id, qty)));
            return items;
        }
        @Override public void set(long userId, long productId, int quantity) { lines.put(productId, quantity); }
        @Override public void remove(long userId, long productId) { lines.remove(productId); }
        @Override public void clear(long userId) { lines.clear(); }
    }

    @Test
    void theSavedCartIsPricedWithPromotionsAndNeverExceedsTheStock() {
        MemoryCart carts = new MemoryCart();
        when(promotions.all()).thenReturn(List.of(peripherals));
        when(catalog.product(7)).thenReturn(Optional.of(mouse));
        when(catalog.productsByIds(anyCollection())).thenReturn(Map.of(7L, mouse));
        CartService service = new CartService(carts, catalog, pricing);

        var view = service.addItem(customer, 7, 2);
        assertThat(view.units()).isEqualTo(2);
        assertThat(view.subtotal()).isEqualByComparingTo("808.20"); // 449.00 − 10 % = 404.10 × 2
        assertThat(view.savings()).isEqualByComparingTo("89.80");
        assertThatThrownBy(() -> service.addItem(customer, 7, 2))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("Solo quedan 3");
        // A guest cart merged after login is clamped to the stock instead of failing.
        assertThat(service.merge(customer, List.of(new CartItem(7, 5))).lines().get(0).quantity()).isEqualTo(3);
        assertThat(service.removeItem(customer, 7).lines()).isEmpty();
        assertThatThrownBy(() -> service.cart(admin)).isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void theCheckoutChargesPromotionPricesPlusShippingInSolesAndEmptiesTheSavedCart() {
        MemoryCart carts = new MemoryCart();
        carts.set(42, 7, 1);
        when(promotions.all()).thenReturn(List.of(peripherals));
        when(catalog.productsByIds(anyCollection())).thenReturn(Map.of(7L, mouse));
        CommerceOrderRepository orders = mock(CommerceOrderRepository.class);
        RiskAssessmentPort risk = mock(RiskAssessmentPort.class);
        when(orders.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(risk.assess(any())).thenReturn(10);
        when(orders.placeOrder(any())).thenAnswer(call -> {
            NewShopOrder placed = call.getArgument(0);
            return new CommerceOrder(placed.order().id(), "cliente", placed.order().amount(), "PEN", 10, OrderStatus.APPROVED,
                    FulfillmentStatus.RISK_APPROVED, "SHOP", 0, Instant.now(clock), placed.lines(), 100123L);
        });
        CommerceService service = new CommerceService(catalog, orders, mock(DeliveryRepository.class), risk, mock(OrderTelemetry.class),
                mock(BusinessTelemetry.class), mock(AuditLog.class), clock, CatalogCache.NONE, pricing, carts);
        ShippingAddress address = new ShippingAddress("Ana Pérez", "Av. Arequipa 1234", "Lima", "999888777", null);

        var result = service.placeOrder(customer, List.of(new CartItem(7, 1)), "key-v6-0001", address,
                DeliveryMethod.EXPRESS, PaymentMethod.CASH_ON_DELIVERY);

        var captor = ArgumentCaptor.forClass(NewShopOrder.class);
        verify(orders).placeOrder(captor.capture());
        NewShopOrder placed = captor.getValue();
        assertThat(placed.order().currency()).isEqualTo("PEN");
        assertThat(placed.checkout().subtotal()).isEqualByComparingTo("404.10");
        assertThat(placed.checkout().shippingCost()).isEqualByComparingTo("29.90");
        assertThat(placed.order().amount()).isEqualByComparingTo("434.00");
        assertThat(placed.lines().get(0).listUnitPrice()).isEqualByComparingTo("449.00");
        var draft = ArgumentCaptor.forClass(OrderDraft.class);
        verify(risk).assess(draft.capture());
        assertThat(draft.getValue().amount()).isEqualByComparingTo("434.00"); // the risk engine sees what is charged
        assertThat(result.order().reference()).isEqualTo("HX-100123");
        assertThat(carts.lines).isEmpty();
    }

    @Test
    void deliveryToAnAddressNeedsTheAddressAndAPaymentMethodCompatibleWithIt() {
        when(catalog.productsByIds(anyCollection())).thenReturn(Map.of(7L, mouse));
        CommerceOrderRepository orders = mock(CommerceOrderRepository.class);
        when(orders.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        RiskAssessmentPort risk = mock(RiskAssessmentPort.class);
        CommerceService service = new CommerceService(catalog, orders, mock(DeliveryRepository.class), risk, mock(OrderTelemetry.class),
                mock(BusinessTelemetry.class), mock(AuditLog.class), clock, CatalogCache.NONE, pricing, CartRepository.NONE);
        assertThatThrownBy(() -> service.placeOrder(customer, List.of(new CartItem(7, 1)), "key-v6-0002", null,
                DeliveryMethod.STANDARD, PaymentMethod.BANK_TRANSFER)).hasMessageContaining("dirección");
        assertThatThrownBy(() -> service.placeOrder(customer, List.of(new CartItem(7, 1)), "key-v6-0003", null,
                DeliveryMethod.PICKUP, PaymentMethod.CASH_ON_DELIVERY)).hasMessageContaining("no está disponible");
        assertThatThrownBy(() -> service.placeOrder(customer, List.of(new CartItem(7, 1)), "key-v6-0004", null,
                DeliveryMethod.PICKUP, null)).hasMessageContaining("paymentMethod");
        verify(risk, never()).assess(any());
        verify(orders, never()).placeOrder(any());
        // Store pickup paid in store needs no address and costs nothing to ship.
        assertThat(service.checkout(List.of(new CartItem(7, 1)), DeliveryMethod.PICKUP, PaymentMethod.PAY_IN_STORE).shippingCost())
                .isEqualByComparingTo("0.00");
    }

    @Test
    void promotionsAreManagedOnlyByTheAdministratorAndMustTargetExistingCategories() {
        when(catalog.categories()).thenReturn(List.of(new Category(50, "perifericos", "Periféricos")));
        when(catalog.products(null, null, true)).thenReturn(List.of(mouse));
        when(promotions.create(any())).thenAnswer(call -> call.getArgument(0));
        when(promotions.all()).thenReturn(List.of(peripherals));
        PromotionService service = new PromotionService(promotions, catalog, CatalogCache.NONE, mock(AuditLog.class), clock);

        assertThat(service.create(admin, peripherals).products()).isEqualTo(1);
        assertThatThrownBy(() -> service.create(customer, peripherals)).isInstanceOf(ForbiddenOperationException.class);
        Promotion unknownCategory = new Promotion(0, "otra", "Otra", "", 5, Instant.parse("2026-11-01T00:00:00Z"),
                Instant.parse("2026-12-01T00:00:00Z"), true, Set.of(999L), Set.of());
        assertThatThrownBy(() -> service.create(admin, unknownCategory)).hasMessageContaining("categoryIds");
        assertThat(service.current()).singleElement().satisfies(view -> assertThat(view.state()).isEqualTo(Promotion.PromotionState.RUNNING));
    }

    @Test
    void theStoreHidesProductsOfHiddenCategoriesAndRejectsUnknownTechnicalFilters() {
        Product hidden = new Product(8, new ProductDetails("BIE-BOTTLE", "Botella", "", 6, new BigDecimal("24.00"), true, 10), "Bienestar", 50);
        when(catalog.categories()).thenReturn(List.of(new Category(50, "perifericos", "Periféricos"),
                new Category(51, "mouse", "Mouse", 50L, 1, true), new Category(6, "bienestar", "Bienestar", null, 9, false)));
        when(catalog.products(null, null, true)).thenReturn(List.of(mouse, hidden));
        when(promotions.all()).thenReturn(List.of());
        CatalogService service = new CatalogService(catalog, mock(AuditLog.class), CatalogCache.NONE, pricing, null);

        assertThat(service.categories()).extracting(Category::slug).containsExactly("perifericos", "mouse");
        assertThat(service.search(CatalogQuery.all()).items()).extracting(p -> p.product().id()).containsExactly(7L);
        CatalogQuery unknown = new CatalogQuery(null, null, null, null, null, false, false, null, Map.of("color", Set.of("rojo")),
                CatalogQuery.Sort.RELEVANCE, 1, 24);
        assertThatThrownBy(() -> service.search(unknown)).hasMessageContaining("Filtro técnico");
        verify(catalog, never()).adjustStock(anyLong(), eq(0));
    }
}
