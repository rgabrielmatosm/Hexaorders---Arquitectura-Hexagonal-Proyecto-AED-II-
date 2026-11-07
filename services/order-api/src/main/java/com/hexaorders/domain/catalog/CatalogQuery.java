package com.hexaorders.domain.catalog;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Store search criteria. Every field is optional. categoryId may be a main category (includes its
 * subcategories) or a subcategory. attributes filters by normalized technical attributes
 * ("ram" → {"16 GB", "32 GB"}): values of the same key are alternatives (OR), keys combine (AND).
 */
public record CatalogQuery(String text, Long categoryId, Set<String> brands, BigDecimal minPrice, BigDecimal maxPrice,
                           boolean onlyAvailable, boolean onlyOffers, CommercialStatus status,
                           Map<String, Set<String>> attributes, Sort sort, int page, int size) {
    public static final int MAX_SIZE = 60;
    public static final int DEFAULT_SIZE = 24;

    public CatalogQuery {
        text = text == null || text.isBlank() ? null : text.strip();
        if (text != null && text.length() > 80) throw new IllegalArgumentException("q: máximo 80 caracteres");
        brands = brands == null ? Set.of() : Set.copyOf(brands);
        if (minPrice != null && minPrice.signum() < 0) throw new IllegalArgumentException("min: no puede ser negativo");
        if (maxPrice != null && maxPrice.signum() < 0) throw new IllegalArgumentException("max: no puede ser negativo");
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) throw new IllegalArgumentException("min: no puede ser mayor que max");
        Map<String, Set<String>> clean = new LinkedHashMap<>();
        if (attributes != null) attributes.forEach((key, values) -> { if (values != null && !values.isEmpty()) clean.put(key, Set.copyOf(values)); });
        if (clean.size() > 12) throw new IllegalArgumentException("Demasiados filtros técnicos");
        attributes = Map.copyOf(clean);
        sort = sort == null ? Sort.RELEVANCE : sort;
        if (page < 1) page = 1;
        if (page > 10000) throw new IllegalArgumentException("page: demasiado alta");
        if (size < 1) size = DEFAULT_SIZE;
        if (size > MAX_SIZE) size = MAX_SIZE;
    }

    public static CatalogQuery all() {
        return new CatalogQuery(null, null, null, null, null, false, false, null, null, Sort.RELEVANCE, 1, DEFAULT_SIZE);
    }

    /** Search words: accents and case are ignored. */
    public List<String> terms() { return text == null ? List.of() : List.of(CatalogSearch.normalize(text).split("\\s+")); }

    public enum Sort {
        RELEVANCE, PRICE_ASC, PRICE_DESC, NAME, NEWEST, POPULAR, DISCOUNT;

        public static Sort parse(String value) {
            if (value == null || value.isBlank()) return RELEVANCE;
            try {
                return Sort.valueOf(value.strip().toUpperCase(java.util.Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException unknown) {
                throw new IllegalArgumentException("sort: relevance, price_asc, price_desc, name, newest, popular o discount");
            }
        }
    }
}
