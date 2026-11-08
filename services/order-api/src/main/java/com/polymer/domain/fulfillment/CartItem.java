package com.polymer.domain.fulfillment;

public record CartItem(long productId, int quantity) {
    public CartItem {
        if (productId <= 0) throw new IllegalArgumentException("productId: obligatorio");
        if (quantity < 1 || quantity > 99) throw new IllegalArgumentException("quantity: entre 1 y 99");
    }
}
