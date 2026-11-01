package com.hexaorders.domain.fulfillment;

/** Who performs a lifecycle transition. SYSTEM is the automatic risk evaluation. */
public enum Actor {
    SYSTEM, CUSTOMER, ADMIN, ORDER_OPERATOR, LOGISTICS_OPERATOR
}
