package com.hexaorders.adapter.in.web;

import com.hexaorders.domain.catalog.Product;
import java.math.BigDecimal;

/** Flat JSON representation of a product. */
public record ProductView(long id, String sku, String name, String description, long categoryId, String categoryName,
                          BigDecimal price, int stock, boolean active, int hue) {
    public static ProductView of(Product p) {
        return new ProductView(p.id(), p.details().sku(), p.name(), p.details().description(), p.details().categoryId(),
                p.categoryName(), p.price(), p.stock(), p.details().active(), p.details().hue());
    }
}
