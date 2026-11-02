package com.hexaorders.application.port.in;

import java.time.Instant;
import java.util.Map;

/** Server-side dependency checks (independent of Prometheus) for OBSERVABILITY_VIEWER. */
public record SystemHealth(boolean database, boolean riskEngine, Map<String, Long> ordersByStatus, long openIncidents, Instant checkedAt) {}
