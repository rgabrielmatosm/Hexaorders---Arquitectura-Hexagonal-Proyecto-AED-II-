package com.hexaorders.application.port.out;

import com.hexaorders.domain.catalog.Promotion;
import java.util.List;
import java.util.Optional;

/** Commercial campaigns and their target categories and products. */
public interface PromotionRepository {
    List<Promotion> all();
    Optional<Promotion> find(long id);
    boolean slugExists(String slug, Long excludingId);
    Promotion create(Promotion promotion);
    Optional<Promotion> update(long id, Promotion promotion);

    /** No promotions (tests and callers without campaigns). */
    PromotionRepository NONE = new PromotionRepository() {
        @Override public List<Promotion> all() { return List.of(); }
        @Override public Optional<Promotion> find(long id) { return Optional.empty(); }
        @Override public boolean slugExists(String slug, Long excludingId) { return false; }
        @Override public Promotion create(Promotion promotion) { throw new UnsupportedOperationException(); }
        @Override public Optional<Promotion> update(long id, Promotion promotion) { return Optional.empty(); }
    };
}
