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
    BigDecimal shopSales();
    long countShopOrders();
    Map<LocalDate, Long> ordersPerDay(Instant since);
    List<AdminDashboard.SalesByCategory> salesByCategory();
}
