package com.hexaorders.domain.fulfillment;

import static com.hexaorders.domain.fulfillment.Actor.ADMIN;
import static com.hexaorders.domain.fulfillment.Actor.CUSTOMER;
import static com.hexaorders.domain.fulfillment.Actor.LOGISTICS_OPERATOR;
import static com.hexaorders.domain.fulfillment.Actor.ORDER_OPERATOR;
import static com.hexaorders.domain.fulfillment.Actor.SYSTEM;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.CANCELLED;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.CONFIRMED;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.CREATED;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.DELIVERED;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.IN_TRANSIT;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.PREPARING;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.READY_FOR_DISPATCH;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.REJECTED;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.RISK_APPROVED;
import static com.hexaorders.domain.fulfillment.FulfillmentStatus.RISK_REVIEW;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Which lifecycle transitions exist and which actors may perform each one. Pure business rule. */
public final class TransitionPolicy {
    private static final Map<FulfillmentStatus, Map<FulfillmentStatus, Set<Actor>>> RULES = new EnumMap<>(FulfillmentStatus.class);

    static {
        allow(CREATED, RISK_APPROVED, SYSTEM);
        allow(CREATED, RISK_REVIEW, SYSTEM);
        allow(RISK_APPROVED, CONFIRMED, ORDER_OPERATOR, ADMIN);
        allow(RISK_APPROVED, CANCELLED, CUSTOMER, ORDER_OPERATOR, ADMIN);
        allow(RISK_REVIEW, CONFIRMED, ORDER_OPERATOR, ADMIN);
        allow(RISK_REVIEW, REJECTED, ORDER_OPERATOR, ADMIN);
        allow(RISK_REVIEW, CANCELLED, CUSTOMER, ADMIN);
        allow(CONFIRMED, PREPARING, ORDER_OPERATOR, ADMIN);
        allow(CONFIRMED, CANCELLED, CUSTOMER, ORDER_OPERATOR, ADMIN);
        allow(PREPARING, READY_FOR_DISPATCH, ORDER_OPERATOR, ADMIN);
        allow(PREPARING, CANCELLED, ORDER_OPERATOR, ADMIN);
        allow(READY_FOR_DISPATCH, IN_TRANSIT, LOGISTICS_OPERATOR, ADMIN);
        allow(IN_TRANSIT, DELIVERED, LOGISTICS_OPERATOR, ADMIN);
    }

    private TransitionPolicy() {}

    private static void allow(FulfillmentStatus from, FulfillmentStatus to, Actor... actors) {
        RULES.computeIfAbsent(from, ignored -> new EnumMap<>(FulfillmentStatus.class))
                .put(to, EnumSet.of(actors[0], actors));
    }

    public static boolean exists(FulfillmentStatus from, FulfillmentStatus to) {
        return RULES.getOrDefault(from, Map.of()).containsKey(to);
    }

    public static boolean allowed(FulfillmentStatus from, FulfillmentStatus to, Actor actor) {
        return RULES.getOrDefault(from, Map.of()).getOrDefault(to, Set.of()).contains(actor);
    }

    /** Next states an actor may move an order to (used to render only valid actions). */
    public static Set<FulfillmentStatus> nextFor(FulfillmentStatus from, Set<Actor> actors) {
        Set<FulfillmentStatus> next = EnumSet.noneOf(FulfillmentStatus.class);
        RULES.getOrDefault(from, Map.of()).forEach((to, who) -> {
            if (who.stream().anyMatch(actors::contains)) next.add(to);
        });
        return next;
    }

    /** Throws when the transition does not exist or the actor may not perform it. */
    public static void check(FulfillmentStatus from, FulfillmentStatus to, Set<Actor> actors) {
        if (!exists(from, to)) {
            throw new TransitionNotAllowedException("Transición inexistente: " + from + " → " + to, false);
        }
        if (actors.stream().noneMatch(actor -> allowed(from, to, actor))) {
            throw new TransitionNotAllowedException("Tu rol no puede mover un pedido de " + from + " a " + to, true);
        }
    }
}
