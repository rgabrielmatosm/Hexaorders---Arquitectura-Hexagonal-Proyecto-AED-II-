package com.polymer.application.port.out;

import com.polymer.domain.fulfillment.CartItem;
import java.util.List;

/** Shopping cart of a signed-in customer, persisted so it survives sessions and devices. */
public interface CartRepository {
    List<CartItem> items(long userId);
    /** Sets the quantity of a product (inserts or updates). */
    void set(long userId, long productId, int quantity);
    void remove(long userId, long productId);
    void clear(long userId);

    /** Without persistence (tests). */
    CartRepository NONE = new CartRepository() {
        @Override public List<CartItem> items(long userId) { return List.of(); }
        @Override public void set(long userId, long productId, int quantity) { }
        @Override public void remove(long userId, long productId) { }
        @Override public void clear(long userId) { }
    };
}
