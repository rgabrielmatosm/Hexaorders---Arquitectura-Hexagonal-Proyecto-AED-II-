package com.polymer.domain.catalog;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * Price a customer pays for a product right now. previousPrice is the crossed-out price (the
 * product's previous price or, when a promotion applies, its regular price); discountPercent is
 * computed from both, never typed by hand.
 */
public record PriceTag(BigDecimal price, BigDecimal listPrice, BigDecimal previousPrice, int discountPercent,
                       Long promotionId, String promotionName) {
    public PriceTag {
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(listPrice, "listPrice");
        if (previousPrice != null && previousPrice.compareTo(price) <= 0) previousPrice = null;
        if (previousPrice == null) discountPercent = 0;
    }

    public boolean onSale() { return previousPrice != null; }

    /** Savings per unit against the crossed-out price (0 when there is no discount). */
    public BigDecimal savings() { return previousPrice == null ? BigDecimal.ZERO.setScale(2) : previousPrice.subtract(price); }

    /** Pure pricing rule: best running promotion that targets the product; previous price shown when it is higher. */
    public static PriceTag of(Product product, Collection<Promotion> promotions, Instant now) {
        BigDecimal regular = product.price();
        Promotion best = promotions == null ? null : promotions.stream()
                .filter(promotion -> promotion.runningAt(now) && promotion.targets(product))
                .max(Comparator.comparingInt(Promotion::discountPercent).thenComparing(Promotion::id, Comparator.reverseOrder()))
                .orElse(null);
        BigDecimal price = best == null ? regular : best.apply(regular);
        BigDecimal compareAt = product.details().compareAtPrice();
        BigDecimal previous = best == null ? compareAt : (compareAt != null && compareAt.compareTo(regular) > 0 ? compareAt : regular);
        int percent = 0;
        if (previous != null && previous.compareTo(price) > 0) {
            percent = previous.subtract(price).multiply(BigDecimal.valueOf(100)).divide(previous, 0, RoundingMode.HALF_UP).intValue();
        }
        return new PriceTag(price, regular, previous, percent, best == null ? null : best.id(), best == null ? null : best.name());
    }

    /** Price without promotions (administration, legacy callers). */
    public static PriceTag regular(Product product) { return of(product, null, Instant.EPOCH); }
}
