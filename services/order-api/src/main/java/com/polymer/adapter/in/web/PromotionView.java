package com.polymer.adapter.in.web;

import com.polymer.application.port.in.PromotionUseCases;
import com.polymer.domain.catalog.Promotion;
import java.time.Instant;
import java.util.Set;

/** JSON of a promotion with its current state (SCHEDULED, RUNNING, EXPIRED, INACTIVE). */
public record PromotionView(long id, String slug, String name, String description, int discountPercent, Instant startsAt,
                            Instant endsAt, boolean active, String state, int products, Set<Long> categoryIds, Set<Long> productIds) {
    static PromotionView of(PromotionUseCases.PromotionView view) {
        Promotion p = view.promotion();
        return new PromotionView(p.id(), p.slug(), p.name(), p.description(), p.discountPercent(), p.startsAt(), p.endsAt(), p.active(),
                view.state().name(), view.products(), p.categoryIds(), p.productIds());
    }
}
