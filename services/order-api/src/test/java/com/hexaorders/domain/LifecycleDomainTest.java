package com.hexaorders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.ProductDetails;
import com.hexaorders.domain.fulfillment.Actor;
import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import com.hexaorders.domain.fulfillment.Quote;
import com.hexaorders.domain.fulfillment.TransitionNotAllowedException;
import com.hexaorders.domain.fulfillment.TransitionPolicy;
import com.hexaorders.domain.identity.UserAccount;
import com.hexaorders.domain.logistics.Delivery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifecycleDomainTest {
    private static Product product(long id, String price, int stock, boolean active) {
        return new Product(id, new ProductDetails("SKU-" + id, "Producto " + id, "", 1, new BigDecimal(price), active, 200), "Cat", stock);
    }

    @Test
    void originalRiskDecisionMapsToTheNewLifecycle() {
        assertThat(FulfillmentStatus.fromRiskDecision(OrderStatus.APPROVED)).isEqualTo(FulfillmentStatus.RISK_APPROVED);
        assertThat(FulfillmentStatus.fromRiskDecision(OrderStatus.REVIEW)).isEqualTo(FulfillmentStatus.RISK_REVIEW);
    }

    @Test
    void happyPathIsAllowedForTheRightRoles() {
        var operator = EnumSet.of(Actor.ORDER_OPERATOR);
        var logistics = EnumSet.of(Actor.LOGISTICS_OPERATOR);
        TransitionPolicy.check(FulfillmentStatus.RISK_APPROVED, FulfillmentStatus.CONFIRMED, operator);
        TransitionPolicy.check(FulfillmentStatus.CONFIRMED, FulfillmentStatus.PREPARING, operator);
        TransitionPolicy.check(FulfillmentStatus.PREPARING, FulfillmentStatus.READY_FOR_DISPATCH, operator);
        TransitionPolicy.check(FulfillmentStatus.READY_FOR_DISPATCH, FulfillmentStatus.IN_TRANSIT, logistics);
        TransitionPolicy.check(FulfillmentStatus.IN_TRANSIT, FulfillmentStatus.DELIVERED, logistics);
    }

    @Test
    void rolesCannotPerformTransitionsOfOtherRoles() {
        assertThatThrownBy(() -> TransitionPolicy.check(FulfillmentStatus.RISK_REVIEW, FulfillmentStatus.CONFIRMED, EnumSet.of(Actor.CUSTOMER)))
                .isInstanceOf(TransitionNotAllowedException.class).matches(e -> ((TransitionNotAllowedException) e).roleDenied());
        assertThatThrownBy(() -> TransitionPolicy.check(FulfillmentStatus.READY_FOR_DISPATCH, FulfillmentStatus.IN_TRANSIT, EnumSet.of(Actor.ORDER_OPERATOR)))
                .isInstanceOf(TransitionNotAllowedException.class);
        assertThatThrownBy(() -> TransitionPolicy.check(FulfillmentStatus.CREATED, FulfillmentStatus.RISK_APPROVED, EnumSet.of(Actor.ADMIN)))
                .isInstanceOf(TransitionNotAllowedException.class);
    }

    @Test
    void nonExistentTransitionsAreRejectedEvenForAdmin() {
        assertThatThrownBy(() -> TransitionPolicy.check(FulfillmentStatus.DELIVERED, FulfillmentStatus.PREPARING, EnumSet.of(Actor.ADMIN)))
                .isInstanceOf(TransitionNotAllowedException.class).matches(e -> !((TransitionNotAllowedException) e).roleDenied());
        assertThat(TransitionPolicy.nextFor(FulfillmentStatus.DELIVERED, EnumSet.allOf(Actor.class))).isEmpty();
        assertThat(FulfillmentStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    void cancellingBeforeDispatchReleasesStockButNotAfter() {
        assertThat(FulfillmentStatus.CONFIRMED.releasesStockWhenMovingTo(FulfillmentStatus.CANCELLED)).isTrue();
        assertThat(FulfillmentStatus.RISK_REVIEW.releasesStockWhenMovingTo(FulfillmentStatus.REJECTED)).isTrue();
        assertThat(FulfillmentStatus.IN_TRANSIT.releasesStockWhenMovingTo(FulfillmentStatus.CANCELLED)).isFalse();
        assertThat(FulfillmentStatus.CONFIRMED.releasesStockWhenMovingTo(FulfillmentStatus.PREPARING)).isFalse();
    }

    @Test
    void quoteIsPricedWithCatalogPricesAndMergesRepeatedProducts() {
        Map<Long, Product> catalog = Map.of(1L, product(1, "19.99", 10, true), 2L, product(2, "5.00", 10, true));
        Quote quote = Quote.price(List.of(new CartItem(1, 2), new CartItem(2, 1), new CartItem(1, 1)), catalog);
        assertThat(quote.lines()).hasSize(2);
        assertThat(quote.lines().get(0).quantity()).isEqualTo(3);
        assertThat(quote.total()).isEqualByComparingTo("64.97");
    }

    @Test
    void quoteRejectsInactiveOrUnknownProductsAndEmptyCarts() {
        assertThatThrownBy(() -> Quote.price(List.of(new CartItem(9, 1)), Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quote.price(List.of(new CartItem(1, 1)), Map.of(1L, product(1, "1.00", 5, false))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Quote.price(List.of(), Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CartItem(1, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deliveryWindowsAreValidatedAndConflictsDetected() {
        Instant nine = Instant.parse("2030-05-01T09:00:00Z");
        Delivery delivery = new Delivery(1, UUID.randomUUID(), 7, "Ana", nine, nine.plusSeconds(7200), null, 0);
        assertThat(delivery.conflictsWith(7, nine.plusSeconds(3600), nine.plusSeconds(10800))).isTrue();
        assertThat(delivery.conflictsWith(8, nine.plusSeconds(3600), nine.plusSeconds(10800))).isFalse();
        assertThat(delivery.conflictsWith(7, nine.plusSeconds(7200), nine.plusSeconds(9000))).isFalse(); // back to back
        assertThatThrownBy(() -> Delivery.validateWindow(nine, nine)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Delivery.validateWindow(nine, nine.plusSeconds(9 * 3600))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Delivery.validateNotInPast(nine, nine.plusSeconds(3600))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void passwordPolicyAndUsernameRules() {
        UserAccount.validatePassword("segura2026");
        assertThatThrownBy(() -> UserAccount.validatePassword("corta1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.validatePassword("soloLetrasLargas")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.validateUsername("a b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.validateEmail("sin-arroba")).isInstanceOf(IllegalArgumentException.class);
    }
}
