package com.polymer.application.port.out;

import com.polymer.domain.incident.Incident;
import com.polymer.domain.incident.IncidentCategory;
import com.polymer.domain.incident.IncidentStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository {
    Incident create(UUID orderId, IncidentCategory category, String description, String reportedBy, Instant at);
    List<Incident> list(IncidentStatus status);
    Optional<Incident> resolve(long id, String resolution, Instant at);
    long countOpen();
}
