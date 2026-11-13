package com.polymer.domain.catalog;

import java.math.BigDecimal;
import java.util.List;

/** One page of store results plus the facets (with counts) that the result set allows. */
public record CatalogPage(List<PricedProduct> items, int total, int page, int size, int pages, Facets facets) {
    public CatalogPage {
        items = List.copyOf(items);
    }

    public record Facets(List<Count> brands, List<CategoryCount> categories, List<AttributeFacet> attributes,
                         BigDecimal minPrice, BigDecimal maxPrice, int offers, int available) {}

    public record Count(String value, int count) {}

    public record CategoryCount(long id, String name, int count) {}

    public record AttributeFacet(String key, String label, List<Count> values) {}
}
