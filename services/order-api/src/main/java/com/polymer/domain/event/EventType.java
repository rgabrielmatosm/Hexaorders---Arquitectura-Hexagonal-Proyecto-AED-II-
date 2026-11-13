package com.polymer.domain.event;

/** Integration events published by POLYMER (contract version 1, see docs/ASYNCHRONOUS_EVENTS.md). */
public enum EventType {
    ORDER_CREATED("order.created"),
    ORDER_APPROVED("order.approved"),
    ORDER_REJECTED("order.rejected"),
    ORDER_STATUS_CHANGED("order.status_changed"),
    DELIVERY_SCHEDULED("delivery.scheduled"),
    /** v7: pago en línea aprobado (tarjeta, Yape o Plin); el consumidor envía la boleta por correo. */
    PAYMENT_CONFIRMED("payment.confirmed");

    private final String routingKey;

    EventType(String routingKey) { this.routingKey = routingKey; }

    /** Topic routing key used by the message broker adapter; part of the public contract. */
    public String routingKey() { return routingKey; }
}
