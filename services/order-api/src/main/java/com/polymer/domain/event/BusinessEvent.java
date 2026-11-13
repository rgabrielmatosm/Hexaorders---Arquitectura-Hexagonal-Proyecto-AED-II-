package com.polymer.domain.event;

import com.polymer.domain.fulfillment.FulfillmentStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A business fact that already happened and was committed to the database. Events carry no
 * personal data (no names, e-mails or addresses): consumers look up what they need by orderId.
 * eventId is deterministic (derived from the committed history row), so a re-delivered event can
 * be recognised and ignored by idempotent consumers.
 */
public record BusinessEvent(String eventId, EventType type, int schemaVersion, Instant occurredAt, UUID orderId,
                            Map<String, String> attributes) {
    public static final int SCHEMA_VERSION = 1;

    public BusinessEvent {
        if (eventId == null || eventId.isBlank() || eventId.length() > 64) throw new IllegalArgumentException("eventId: obligatorio (máximo 64)");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(orderId, "orderId");
        if (schemaVersion < 1) throw new IllegalArgumentException("schemaVersion: debe ser >= 1");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    /**
     * Maps a committed lifecycle change of a shop order to its integration event. This is a pure
     * business rule: which changes count as "created", "approved" or "rejected".
     */
    public static BusinessEvent fromStatusChange(long historyId, UUID orderId, FulfillmentStatus from, FulfillmentStatus to,
                                                 Instant at) {
        Objects.requireNonNull(to, "to");
        EventType type;
        Map<String, String> attributes = new LinkedHashMap<>();
        if (from == null && to == FulfillmentStatus.CREATED) {
            type = EventType.ORDER_CREATED;
        } else if (to == FulfillmentStatus.RISK_APPROVED || to == FulfillmentStatus.CONFIRMED) {
            type = EventType.ORDER_APPROVED;
            attributes.put("approvedBy", to == FulfillmentStatus.RISK_APPROVED ? "risk-engine" : "operations");
        } else if (to == FulfillmentStatus.REJECTED) {
            type = EventType.ORDER_REJECTED;
        } else {
            type = EventType.ORDER_STATUS_CHANGED;
        }
        if (from != null) attributes.put("from", from.name());
        attributes.put("to", to.name());
        return new BusinessEvent("order-status-" + historyId, type, SCHEMA_VERSION, at, orderId, attributes);
    }

    /** A delivery was scheduled (action CREATED) or rescheduled (action RESCHEDULED). */
    public static BusinessEvent fromDeliveryChange(long changeId, UUID orderId, String action, Instant windowStart,
                                                   Instant windowEnd, Instant at) {
        if (!"CREATED".equals(action) && !"RESCHEDULED".equals(action)) throw new IllegalArgumentException("action: CREATED o RESCHEDULED");
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("action", action);
        attributes.put("windowStart", Objects.requireNonNull(windowStart, "windowStart").toString());
        attributes.put("windowEnd", Objects.requireNonNull(windowEnd, "windowEnd").toString());
        return new BusinessEvent("delivery-change-" + changeId, EventType.DELIVERY_SCHEDULED, SCHEMA_VERSION, at, orderId, attributes);
    }

    /** v7: pago aprobado. eventId determinista (el id del pago): una reentrega no envía dos boletas. */
    public static BusinessEvent fromPayment(UUID paymentId, UUID orderId, String method, String receipt, Instant paidAt) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("method", Objects.requireNonNull(method, "method"));
        attributes.put("receipt", Objects.requireNonNull(receipt, "receipt"));
        return new BusinessEvent("payment-" + paymentId, EventType.PAYMENT_CONFIRMED, SCHEMA_VERSION, paidAt, orderId, attributes);
    }
}
