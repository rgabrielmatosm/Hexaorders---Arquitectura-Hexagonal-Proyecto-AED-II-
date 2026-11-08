package com.polymer.domain.monitoring;

import java.time.Instant;
import java.util.List;

/** What the monitoring portal shows. available = false when the monitoring system did not answer. */
public record MonitoringSnapshot(boolean available, Instant checkedAt, List<ServiceTarget> targets,
                                 List<AlertRule> alerts, List<Indicator> indicators) {
    public MonitoringSnapshot {
        targets = List.copyOf(targets);
        alerts = List.copyOf(alerts);
        indicators = List.copyOf(indicators);
    }
}
