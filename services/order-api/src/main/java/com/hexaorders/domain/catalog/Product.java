package com.hexaorders.domain.catalog;

import java.math.BigDecimal;
import java.util.Objects;

public record Product(long id, ProductDetails details, String categoryName, int stock) {
    public Product {
        Objects.requireNonNull(details, "details");
        if (stock < 0) {
            throw new IllegalArgumentException("stock: no puede ser negativo");
        }
    }

    public BigDecimal price() { return details.price(); }
    public String name() { return details.name(); }
    public boolean available(int quantity) { return details.active() && stock >= quantity; }
}
