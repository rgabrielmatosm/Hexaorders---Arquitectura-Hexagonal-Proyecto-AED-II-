package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.Quote;
import java.util.List;
import java.util.UUID;

public interface CommerceUseCases {
    Quote quote(List<CartItem> items);
    PlacementResult placeOrder(AuthenticatedUser customer, List<CartItem> items, String idempotencyKey);
    List<CommerceOrder> myOrders(AuthenticatedUser customer);
    /** Only the owner can read the order; any other caller gets "not found". */
    OrderDetails myOrder(AuthenticatedUser customer, UUID orderId);
    CommerceOrder cancelMyOrder(AuthenticatedUser customer, UUID orderId);
}
