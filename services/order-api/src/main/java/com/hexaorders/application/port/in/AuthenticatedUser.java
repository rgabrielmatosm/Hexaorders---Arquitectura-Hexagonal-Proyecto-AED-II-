package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.Actor;
import com.hexaorders.domain.identity.Role;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Identity of the caller as seen by the application core (no framework types). */
public record AuthenticatedUser(long userId, String username, Set<Role> roles) {
    public AuthenticatedUser {
        Objects.requireNonNull(username, "username");
        roles = roles == null || roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    public boolean has(Role role) { return roles.contains(role); }

    public boolean hasAny(Role... candidates) {
        for (Role role : candidates) if (roles.contains(role)) return true;
        return false;
    }

    /** Lifecycle actors corresponding to the user's roles. */
    public Set<Actor> actors() {
        Set<Actor> actors = EnumSet.noneOf(Actor.class);
        if (has(Role.CUSTOMER)) actors.add(Actor.CUSTOMER);
        if (has(Role.ADMIN)) actors.add(Actor.ADMIN);
        if (has(Role.ORDER_OPERATOR)) actors.add(Actor.ORDER_OPERATOR);
        if (has(Role.LOGISTICS_OPERATOR)) actors.add(Actor.LOGISTICS_OPERATOR);
        return actors;
    }
}
