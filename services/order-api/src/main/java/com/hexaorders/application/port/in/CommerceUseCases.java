package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.CheckoutSummary;
import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.fulfillment.DeliveryMethod;
import com.hexaorders.domain.fulfillment.PaymentMethod;
import com.hexaorders.domain.fulfillment.Quote;
import com.hexaorders.domain.fulfillment.ShippingAddress;
import java.util.List;
import java.util.UUID;

public interface CommerceUseCases {
    Quote quote(List<CartItem> items);
    /** v6: totals of a checkout (products with promotions + shipping) before confirming. */
    CheckoutSummary checkout(List<CartItem> items, DeliveryMethod delivery, PaymentMethod payment);
    /** v3 contract (no shipping data), kept for compatibility. */
    default PlacementResult placeOrder(AuthenticatedUser customer, List<CartItem> items, String idempotencyKey) {
        return placeOrder(customer, items, idempotencyKey, null);
    }
    /** v4: the web checkout sends the shipping address; it is optional for API clients. */
    PlacementResult placeOrder(AuthenticatedUser customer, List<CartItem> items, String idempotencyKey, ShippingAddress shipping);
    /** v6: delivery and payment methods chosen at checkout. Delivery to an address requires the address. */
    PlacementResult placeOrder(AuthenticatedUser customer, List<CartItem> items, String idempotencyKey, ShippingAddress shipping,
                               DeliveryMethod delivery, PaymentMethod payment);
    List<CommerceOrder> myOrders(AuthenticatedUser customer);
    /** Only the owner can read the order; any other caller gets "not found". */
    OrderDetails myOrder(AuthenticatedUser customer, UUID orderId);
    CommerceOrder cancelMyOrder(AuthenticatedUser customer, UUID orderId);
}
