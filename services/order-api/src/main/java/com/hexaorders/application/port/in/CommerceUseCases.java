package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.Quote;
import com.hexaorders.domain.fulfillment.ShippingAddress;
import java.util.List;
import java.util.UUID;

public interface CommerceUseCases {
    Quote quote(List<CartItem> items);
    /** v3 contract (no shipping data), kept for compatibility. */
    default PlacementResult placeOrder(AuthenticatedUser customer, List<CartItem> items, String idempotencyKey) {
        return placeOrder(customer, items, idempotencyKey, null);
    }
    /** v4: the web checkout sends the shipping address; it is optional for API clients. */
    PlacementResult placeOrder(AuthenticatedUser customer, List<CartItem> items, String idempotencyKey, ShippingAddress shipping);
    List<CommerceOrder> myOrders(AuthenticatedUser customer);
    /** Only the owner can read the order; any other caller gets "not found". */
    OrderDetails myOrder(AuthenticatedUser customer, UUID orderId);
    CommerceOrder cancelMyOrder(AuthenticatedUser customer, UUID orderId);
}
