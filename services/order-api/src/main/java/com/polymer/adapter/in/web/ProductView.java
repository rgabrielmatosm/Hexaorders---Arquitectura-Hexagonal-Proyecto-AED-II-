package com.polymer.adapter.in.web;

import com.polymer.domain.catalog.PriceTag;
import com.polymer.domain.catalog.PricedProduct;
import com.polymer.domain.catalog.Product;
import com.polymer.domain.catalog.ProductDetails;
import com.polymer.domain.catalog.Spec;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Flat JSON representation of a product. price is what the customer pays now (promotions applied);
 * listPrice is the regular price; previousPrice is the crossed-out price (null without discount).
 * The v2–v5 fields keep their names and meaning.
 */
public record ProductView(long id, String sku, String name, String description, long categoryId, String categoryName,
                          BigDecimal price, int stock, boolean active, int hue,
                          String currency, BigDecimal listPrice, BigDecimal previousPrice, int discountPercent, String promotionName,
                          String brand, String model, String longDescription, List<Spec> specs, Map<String, String> attributes,
                          BigDecimal compareAtPrice, String status, String statusLabel, String availability, String availabilityLabel,
                          Long mainCategoryId, String mainCategoryName, Instant createdAt, Instant updatedAt) {

    /** Regular price (administration and v2–v5 endpoints). */
    public static ProductView of(Product p) { return of(p, PriceTag.regular(p)); }

    public static ProductView of(PricedProduct priced) { return of(priced.product(), priced.price()); }

    private static ProductView of(Product p, PriceTag tag) {
        ProductDetails d = p.details();
        return new ProductView(p.id(), d.sku(), p.name(), d.description(), d.categoryId(), p.categoryName(), tag.price(), p.stock(),
                d.active(), d.hue(), "PEN", tag.listPrice(), tag.previousPrice(), tag.discountPercent(), tag.promotionName(),
                d.brand(), d.model(), d.longDescription(), d.specs(), d.attributes(), d.compareAtPrice(), d.status().name(),
                d.status().label(), p.availability().name(), p.availability().label(), p.mainCategoryId(), p.mainCategoryName(),
                p.createdAt(), p.updatedAt());
    }
}
