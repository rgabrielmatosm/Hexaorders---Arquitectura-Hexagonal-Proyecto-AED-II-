package com.hexaorders.application.service;

import com.hexaorders.application.port.out.PromotionRepository;
import com.hexaorders.domain.catalog.PriceTag;
import com.hexaorders.domain.catalog.PricedProduct;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.Promotion;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * Prices the catalog with the promotions running now. Used by the store, the cart and the checkout,
 * so a customer always pays the price shown. The rule itself lives in the domain (PriceTag).
 */
public final class PricingService {
    private final PromotionRepository promotions;
    private final Clock clock;

    public PricingService(PromotionRepository promotions, Clock clock) {
        this.promotions = promotions;
        this.clock = clock;
    }

    /** Regular prices only (no campaigns). */
    public static PricingService none(Clock clock) { return new PricingService(PromotionRepository.NONE, clock); }

    public Instant now() { return clock.instant(); }

    /** One read of the running promotions, reused for every product priced with the returned function. */
    public Function<Product, PriceTag> pricer() {
        Instant now = clock.instant();
        List<Promotion> running = promotions.all().stream().filter(promotion -> promotion.runningAt(now)).toList();
        return product -> PriceTag.of(product, running, now);
    }

    public List<PricedProduct> price(List<Product> products) {
        Function<Product, PriceTag> pricer = pricer();
        return products.stream().map(product -> new PricedProduct(product, pricer.apply(product))).toList();
    }

    public PricedProduct price(Product product) { return new PricedProduct(product, pricer().apply(product)); }
}
