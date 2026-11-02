package com.hexaorders.application.service;

import com.hexaorders.application.exception.BusinessConflictException;
import com.hexaorders.application.exception.IdempotencyKeyReusedException;
import com.hexaorders.application.exception.OutOfStockException;
import com.hexaorders.application.exception.ResourceNotFoundException;
import com.hexaorders.application.exception.RiskUnavailableException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.CommerceUseCases;
import com.hexaorders.application.port.in.OrderDetails;
import com.hexaorders.application.port.in.PlacementResult;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.BusinessTelemetry;
import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.application.port.out.CommerceOrderRepository;
import com.hexaorders.application.port.out.DeliveryRepository;
import com.hexaorders.application.port.out.NewShopOrder;
import com.hexaorders.application.port.out.OrderTelemetry;
import com.hexaorders.application.port.out.RiskAssessmentPort;
import com.hexaorders.domain.Order;
import com.hexaorders.domain.OrderDraft;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.fulfillment.Actor;
import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.OrderLine;
import com.hexaorders.domain.fulfillment.Quote;
import com.hexaorders.domain.fulfillment.StatusChange;
import com.hexaorders.domain.fulfillment.TransitionPolicy;
import com.hexaorders.domain.identity.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Customer use cases. Placing an order reuses the ORIGINAL risk flow: the same RiskAssessmentPort
 * (Python engine) is called before persisting, and the same OrderTelemetry counter is updated.
 */
public final class CommerceService implements CommerceUseCases {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final CatalogRepository catalog;
    private final CommerceOrderRepository orders;
    private final DeliveryRepository deliveries;
    private final RiskAssessmentPort risk;
    private final OrderTelemetry orderTelemetry;
    private final BusinessTelemetry telemetry;
    private final AuditLog audit;
    private final Clock clock;
    private final LifecycleTransitions transitions;

    public CommerceService(CatalogRepository catalog, CommerceOrderRepository orders, DeliveryRepository deliveries,
                           RiskAssessmentPort risk, OrderTelemetry orderTelemetry, BusinessTelemetry telemetry,
                           AuditLog audit, Clock clock) {
        this.catalog = catalog;
        this.orders = orders;
        this.deliveries = deliveries;
        this.risk = risk;
        this.orderTelemetry = orderTelemetry;
        this.telemetry = telemetry;
        this.audit = audit;
        this.clock = clock;
        this.transitions = new LifecycleTransitions(orders, deliveries, telemetry, audit, clock);
    }

    @Override
    public Quote quote(List<CartItem> items) {
        if (items == null || items.isEmpty()) throw new IllegalArgumentException("El carrito está vacío");
        Map<Long, Product> products = catalog.productsByIds(items.stream().map(CartItem::productId).toList());
        return Quote.price(items, products);
    }

    @Override
    public PlacementResult placeOrder(AuthenticatedUser customer, List<CartItem> items, String idempotencyKey,
                                      com.hexaorders.domain.fulfillment.ShippingAddress shipping) {
        Guards.require(customer, Role.CUSTOMER);
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new IllegalArgumentException("Idempotency-Key: obligatoria, 8 a 64 caracteres [A-Za-z0-9_-]");
        }
        var replay = replay(customer, idempotencyKey);
        if (replay != null) return replay;

        Quote quote;
        try {
            quote = quote(items);
        } catch (IllegalArgumentException invalid) {
            telemetry.checkoutFailed("invalid");
            throw invalid;
        }
        Map<Long, Product> products = catalog.productsByIds(quote.lines().stream().map(OrderLine::productId).toList());
        for (OrderLine line : quote.lines()) {
            if (!products.get(line.productId()).available(line.quantity())) {
                telemetry.checkoutFailed("out_of_stock");
                throw new OutOfStockException(line.productName());
            }
        }

        // 1) Risk first (Python), exactly like the original use case; no transaction is open yet.
        OrderDraft draft = new OrderDraft(customer.username(), quote.total(), "USD");
        int score;
        try {
            score = risk.assess(draft);
            if (score < 0 || score > 100) throw new RiskUnavailableException("El motor de riesgo devolvió un puntaje inválido");
        } catch (RiskUnavailableException error) {
            orderTelemetry.riskFailure();
            telemetry.checkoutFailed("risk_unavailable");
            throw error;
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Order order = Order.create(draft, score, UUID.randomUUID(), now);
        FulfillmentStatus status = FulfillmentStatus.fromRiskDecision(order.status());
        List<StatusChange> history = List.of(
                new StatusChange(null, FulfillmentStatus.CREATED, customer.username(), "Pedido creado desde el portal", now),
                new StatusChange(FulfillmentStatus.CREATED, status, "risk-engine", "riskScore=" + score, now));

        // 2) One transaction: order + lines + history + conditional stock decrement.
        CommerceOrder placed;
        try {
            placed = orders.placeOrder(new NewShopOrder(order, customer.userId(), status, quote.lines(), history, idempotencyKey, shipping));
        } catch (OutOfStockException outOfStock) {
            telemetry.checkoutFailed("out_of_stock");
            throw outOfStock;
        } catch (IdempotencyKeyReusedException concurrent) {
            var again = replay(customer, idempotencyKey);
            if (again != null) return again;
            throw new BusinessConflictException("IDEMPOTENCY_KEY_REUSED", concurrent.getMessage());
        }
        orderTelemetry.orderCreated(order.status());
        telemetry.shopOrderPlaced(order.status());
        audit.record(customer.username(), "ORDER_PLACED", order.id().toString(),
                "total=" + quote.total() + " USD; lines=" + quote.lines().size() + "; riskScore=" + score + "; status=" + status);
        return new PlacementResult(placed, true);
    }

    private PlacementResult replay(AuthenticatedUser customer, String key) {
        return orders.findByIdempotencyKey(key).map(existing -> {
            Long owner = orders.ownerOf(existing.id()).orElse(null);
            if (owner == null || owner != customer.userId()) {
                throw new BusinessConflictException("IDEMPOTENCY_KEY_REUSED", "Idempotency-Key ya utilizada por otra solicitud");
            }
            return new PlacementResult(existing, false);
        }).orElse(null);
    }

    @Override
    public List<CommerceOrder> myOrders(AuthenticatedUser customer) {
        Guards.require(customer, Role.CUSTOMER);
        return orders.byCustomer(customer.userId());
    }

    @Override
    public OrderDetails myOrder(AuthenticatedUser customer, UUID orderId) {
        Guards.require(customer, Role.CUSTOMER);
        ensureOwner(customer, orderId);
        CommerceOrder order = orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return new OrderDetails(order, orders.history(orderId), deliveries.findByOrder(orderId).orElse(null),
                TransitionPolicy.nextFor(order.status(), EnumSet.of(Actor.CUSTOMER)), orders.shippingOf(orderId).orElse(null));
    }

    @Override
    public CommerceOrder cancelMyOrder(AuthenticatedUser customer, UUID orderId) {
        Guards.require(customer, Role.CUSTOMER);
        ensureOwner(customer, orderId);
        return transitions.apply(customer, EnumSet.of(Actor.CUSTOMER), orderId, FulfillmentStatus.CANCELLED, "Cancelado por el cliente");
    }

    /** Orders of other customers are reported as "not found" to avoid revealing that they exist. */
    private void ensureOwner(AuthenticatedUser customer, UUID orderId) {
        Long owner = orders.ownerOf(orderId).orElse(null);
        if (owner == null || owner != customer.userId()) throw new ResourceNotFoundException("Pedido no encontrado");
    }
}
