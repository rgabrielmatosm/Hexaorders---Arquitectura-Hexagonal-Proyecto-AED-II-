package com.hexaorders.application.service;

import com.hexaorders.application.exception.BusinessConflictException;
import com.hexaorders.application.exception.ResourceNotFoundException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.PromotionUseCases;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.CatalogCache;
import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.application.port.out.PromotionRepository;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.Promotion;
import com.hexaorders.domain.identity.Role;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class PromotionService implements PromotionUseCases {
    private final PromotionRepository promotions;
    private final CatalogRepository catalog;
    private final CatalogCache cache;
    private final AuditLog audit;
    private final Clock clock;

    public PromotionService(PromotionRepository promotions, CatalogRepository catalog, CatalogCache cache, AuditLog audit, Clock clock) {
        this.promotions = promotions;
        this.catalog = catalog;
        this.cache = cache;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<PromotionView> current() {
        Instant now = clock.instant();
        List<Product> active = cache.activeProducts(null, () -> catalog.products(null, null, true));
        return promotions.all().stream().filter(p -> p.runningAt(now))
                .sorted(Comparator.comparing(Promotion::endsAt))
                .map(p -> view(p, now, active)).toList();
    }

    @Override
    public List<PromotionView> all(AuthenticatedUser admin) {
        Guards.require(admin, Role.ADMIN);
        Instant now = clock.instant();
        List<Product> active = catalog.products(null, null, true);
        return promotions.all().stream().sorted(Comparator.comparing(Promotion::startsAt).reversed())
                .map(p -> view(p, now, active)).toList();
    }

    @Override
    public PromotionView create(AuthenticatedUser admin, Promotion promotion) {
        Guards.require(admin, Role.ADMIN);
        validateTargets(promotion);
        if (promotions.slugExists(promotion.slug(), null)) throw new BusinessConflictException("DUPLICATE_PROMOTION", "Ya existe una promoción con ese identificador");
        Promotion created = promotions.create(promotion);
        audit.record(admin.username(), "PROMOTION_CREATED", "promotion:" + created.id(), describe(created));
        return view(created, clock.instant(), catalog.products(null, null, true));
    }

    @Override
    public PromotionView update(AuthenticatedUser admin, long id, Promotion promotion) {
        Guards.require(admin, Role.ADMIN);
        validateTargets(promotion);
        if (promotions.slugExists(promotion.slug(), id)) throw new BusinessConflictException("DUPLICATE_PROMOTION", "Ya existe una promoción con ese identificador");
        Promotion updated = promotions.update(id, promotion).orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
        audit.record(admin.username(), "PROMOTION_UPDATED", "promotion:" + id, describe(updated));
        return view(updated, clock.instant(), catalog.products(null, null, true));
    }

    @Override
    public PromotionView setActive(AuthenticatedUser admin, long id, boolean active) {
        Guards.require(admin, Role.ADMIN);
        Promotion p = promotions.find(id).orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
        Promotion changed = new Promotion(p.id(), p.slug(), p.name(), p.description(), p.discountPercent(), p.startsAt(), p.endsAt(),
                active, p.categoryIds(), p.productIds());
        Promotion saved = promotions.update(id, changed).orElseThrow(() -> new ResourceNotFoundException("Promoción no encontrada"));
        audit.record(admin.username(), active ? "PROMOTION_ACTIVATED" : "PROMOTION_DEACTIVATED", "promotion:" + id, p.slug());
        return view(saved, clock.instant(), catalog.products(null, null, true));
    }

    private void validateTargets(Promotion promotion) {
        Set<Long> categories = catalog.categories().stream().map(Category::id).collect(Collectors.toSet());
        if (!categories.containsAll(promotion.categoryIds())) throw new IllegalArgumentException("categoryIds: alguna categoría no existe");
        if (!promotion.productIds().isEmpty() && catalog.productsByIds(promotion.productIds()).size() != promotion.productIds().size()) {
            throw new IllegalArgumentException("productIds: algún producto no existe");
        }
    }

    private static PromotionView view(Promotion promotion, Instant now, List<Product> active) {
        int count = (int) active.stream().filter(promotion::targets).count();
        return new PromotionView(promotion, promotion.stateAt(now), count);
    }

    private static String describe(Promotion p) {
        return p.slug() + " · " + p.discountPercent() + "% · " + p.startsAt() + " → " + p.endsAt() + " · active=" + p.active();
    }
}
