package com.polymer.domain.identity;

/** Business roles of the platform. Authorization decisions are taken in the backend. */
public enum Role {
    CUSTOMER, ADMIN, ORDER_OPERATOR, LOGISTICS_OPERATOR, OBSERVABILITY_VIEWER
}
