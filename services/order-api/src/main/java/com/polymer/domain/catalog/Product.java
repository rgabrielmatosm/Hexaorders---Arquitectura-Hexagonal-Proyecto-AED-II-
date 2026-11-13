package com.polymer.domain.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/**
 * A catalog product with its stock. categoryName is its (sub)category; mainCategoryId/Name is the
 * main category it belongs to (the same category when the product hangs from a main category).
 */
public record Product(long id, ProductDetails details, String categoryName, int stock,
                      Long mainCategoryId, String mainCategoryName, Instant createdAt, Instant updatedAt) {
    public Product {
        Objects.requireNonNull(details, "details");
        if (stock < 0) {
            throw new IllegalArgumentException("stock: no puede ser negativo");
        }
        if (mainCategoryId == null) {
            mainCategoryId = details.categoryId();
            mainCategoryName = categoryName;
        }
    }

    /** v2–v5 constructor. */
    public Product(long id, ProductDetails details, String categoryName, int stock) {
        this(id, details, categoryName, stock, null, null, null, null);
    }

    public BigDecimal price() { return details.price(); }
    public String name() { return details.name(); }
    public boolean available(int quantity) { return details.active() && stock >= quantity; }
    public Availability availability() { return Availability.of(details.active(), stock); }
    public boolean belongsTo(long categoryId) { return details.categoryId() == categoryId || Objects.equals(mainCategoryId, categoryId); }
}
