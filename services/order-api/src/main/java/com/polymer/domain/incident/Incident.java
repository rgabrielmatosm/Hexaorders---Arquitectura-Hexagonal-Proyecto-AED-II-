package com.polymer.domain.incident;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Incident(long id, UUID orderId, IncidentCategory category, String description, IncidentStatus status,
                       String reportedBy, Instant createdAt, Instant resolvedAt, String resolution) {
    public Incident {
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        validateDescription(description);
    }

    public static void validateDescription(String description) {
        if (description == null || description.isBlank() || description.length() > 1000) {
            throw new IllegalArgumentException("description: obligatoria, máximo 1000 caracteres");
        }
    }
}
