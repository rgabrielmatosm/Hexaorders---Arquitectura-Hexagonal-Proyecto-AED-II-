package com.polymer.domain.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Commercial campaign: a percentage discount, valid between two dates, for a set of categories
 * (main categories include their subcategories) and/or individual products. Discounts never stack:
 * a product gets the best running promotion that targets it.
 */
public record Promotion(long id, String slug, String name, String description, int discountPercent,
                        Instant startsAt, Instant endsAt, boolean active, Set<Long> categoryIds, Set<Long> productIds) {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public Promotion {
        if (slug == null || slug.length() > 48 || !SLUG.matcher(slug).matches()) throw new IllegalArgumentException("slug: minúsculas, dígitos y guiones (máximo 48)");
        if (name == null || name.isBlank() || name.length() > 80) throw new IllegalArgumentException("name: obligatorio, máximo 80 caracteres");
        description = description == null ? "" : description.strip();
        if (description.length() > 300) throw new IllegalArgumentException("description: máximo 300 caracteres");
        if (discountPercent < 1 || discountPercent > 90) throw new IllegalArgumentException("discountPercent: entre 1 y 90");
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!endsAt.isAfter(startsAt)) throw new IllegalArgumentException("endsAt: debe ser posterior a startsAt");
        categoryIds = categoryIds == null ? Set.of() : Set.copyOf(categoryIds);
        productIds = productIds == null ? Set.of() : Set.copyOf(productIds);
        if (categoryIds.isEmpty() && productIds.isEmpty()) throw new IllegalArgumentException("La promoción debe aplicarse al menos a una categoría o un producto");
        name = name.strip();
    }

    public boolean runningAt(Instant now) { return active && !now.isBefore(startsAt) && now.isBefore(endsAt); }

    public PromotionState stateAt(Instant now) {
        if (!active) return PromotionState.INACTIVE;
        if (now.isBefore(startsAt)) return PromotionState.SCHEDULED;
        return now.isBefore(endsAt) ? PromotionState.RUNNING : PromotionState.EXPIRED;
    }

    public boolean targets(Product product) {
        return productIds.contains(product.id()) || categoryIds.contains(product.details().categoryId())
                || (product.mainCategoryId() != null && categoryIds.contains(product.mainCategoryId()));
    }

    /** price × (100 − discount) / 100, rounded half up to cents. */
    public BigDecimal apply(BigDecimal price) {
        return price.multiply(BigDecimal.valueOf(100L - discountPercent)).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public enum PromotionState { SCHEDULED, RUNNING, EXPIRED, INACTIVE }
}
