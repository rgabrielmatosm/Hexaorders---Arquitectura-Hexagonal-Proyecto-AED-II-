package com.hexaorders.application.port.out;

import com.hexaorders.domain.incident.Incident;
import com.hexaorders.domain.incident.IncidentCategory;
import com.hexaorders.domain.incident.IncidentStatus;
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
