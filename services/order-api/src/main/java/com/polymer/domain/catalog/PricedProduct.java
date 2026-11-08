package com.polymer.domain.catalog;

import java.util.Objects;

/** A product together with the price a customer pays for it now. */
public record PricedProduct(Product product, PriceTag price) {
    public PricedProduct {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(price, "price");
    }
}
