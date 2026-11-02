package com.hexaorders.domain.fulfillment;

import java.time.Instant;
import java.util.Objects;

/** One entry of the order history. from is null for the first entry. */
public record StatusChange(FulfillmentStatus from, FulfillmentStatus to, String actor, String note, Instant at) {
    public StatusChange {
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(at, "at");
        if (note != null && note.length() > 500) throw new IllegalArgumentException("note: máximo 500 caracteres");
    }
}
