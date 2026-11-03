package com.hexaorders.application.port.out;

import com.hexaorders.application.port.in.AdminDashboard;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.StatusChange;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CommerceOrderRepository {
    /**
     * One transaction: INSERT order, lines and history, and decrement stock with a conditional
     * UPDATE (stock >= quantity). Throws OutOfStockException or IdempotencyKeyReusedException.
     */
    CommerceOrder placeOrder(NewShopOrder order);
    Optional<CommerceOrder> findById(UUID id);
    Optional<CommerceOrder> findByIdempotencyKey(String key);
    Optional<Long> ownerOf(UUID orderId);
    /** Shipping data of a shop order (empty for lab orders and v3 orders). */
    Optional<com.hexaorders.domain.fulfillment.ShippingAddress> shippingOf(UUID orderId);
    /** v6: delivery, payment and totals of a store order (empty for orders placed before v6). */
    default Optional<com.hexaorders.domain.fulfillment.OrderCheckout> checkoutOf(UUID orderId) { return Optional.empty(); }
    List<CommerceOrder> byCustomer(long userId);
    List<CommerceOrder> byStatuses(Set<FulfillmentStatus> statuses, int limit);
    List<StatusChange> history(UUID orderId);
    /**
     * Optimistic transition: succeeds only if the order still has expectedVersion and expectedStatus.
     * When releaseStock is true, the order lines are returned to stock in the same transaction.
     */
    boolean transition(UUID orderId, int expectedVersion, FulfillmentStatus expectedStatus, StatusChange change, boolean releaseStock);
    Map<String, Long> countByStatus();
    long countAll();
    /** Store sales in PEN (cancelled and rejected orders excluded). */
    BigDecimal shopSales();
    /** Store sales placed in USD before v6. */
    default BigDecimal legacyUsdSales() { return BigDecimal.ZERO.setScale(2); }
    long countShopOrders();
    Map<LocalDate, Long> ordersPerDay(Instant since);
    List<AdminDashboard.SalesByCategory> salesByCategory();
    /** Best-selling products by units (cancelled and rejected orders excluded). */
    default List<AdminDashboard.TopProduct> topProducts(int limit) { return List.of(); }
    /** Units sold per product (for the "most popular" order in the store). */
    default Map<Long, Long> unitsSold() { return Map.of(); }
}
