package com.hexaorders.application.port.in;

import com.hexaorders.domain.catalog.Promotion;
import java.util.List;

/** Commercial campaigns: the store shows the running ones; the administrator manages them. */
public interface PromotionUseCases {
    /** Running promotions with the number of products they discount right now. */
    List<PromotionView> current();
    List<PromotionView> all(AuthenticatedUser admin);
    PromotionView create(AuthenticatedUser admin, Promotion promotion);
    PromotionView update(AuthenticatedUser admin, long id, Promotion promotion);
    PromotionView setActive(AuthenticatedUser admin, long id, boolean active);

    record PromotionView(Promotion promotion, Promotion.PromotionState state, int products) {}
}
