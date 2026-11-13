package com.polymer.domain.logistics;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A scheduled delivery window for one order, assigned to one logistics operator. */
public record Delivery(long id, UUID orderId, long assigneeId, String assigneeName, Instant start, Instant end,
                       String notes, int version) {
    public static final Duration MAX_WINDOW = Duration.ofHours(8);

    public Delivery {
        Objects.requireNonNull(orderId, "orderId");
        validateWindow(start, end);
        if (notes != null && notes.length() > 500) throw new IllegalArgumentException("notes: máximo 500 caracteres");
    }

    public static void validateWindow(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("La ventana de entrega debe terminar después de empezar");
        }
        if (Duration.between(start, end).compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("La ventana de entrega no puede superar 8 horas");
        }
    }

    /** New windows may not start in the past (a small tolerance absorbs clock skew). */
    public static void validateNotInPast(Instant start, Instant now) {
        if (start.isBefore(now.minus(Duration.ofMinutes(5)))) {
            throw new IllegalArgumentException("No se puede programar una entrega en el pasado");
        }
    }

    /** Same operator and overlapping time windows: a scheduling conflict. */
    public boolean conflictsWith(long otherAssignee, Instant otherStart, Instant otherEnd) {
        return assigneeId == otherAssignee && start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}
