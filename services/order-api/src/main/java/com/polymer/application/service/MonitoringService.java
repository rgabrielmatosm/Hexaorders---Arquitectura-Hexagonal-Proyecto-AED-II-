package com.polymer.application.service;

import com.polymer.application.exception.MonitoringUnavailableException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.MonitoringUseCases;
import com.polymer.application.port.out.MonitoringPort;
import com.polymer.domain.identity.Role;
import com.polymer.domain.monitoring.MonitoringSnapshot;
import java.time.Clock;
import java.util.List;

public final class MonitoringService implements MonitoringUseCases {
    private final MonitoringPort monitoring;
    private final Clock clock;

    public MonitoringService(MonitoringPort monitoring, Clock clock) {
        this.monitoring = monitoring;
        this.clock = clock;
    }

    @Override
    public MonitoringSnapshot snapshot(AuthenticatedUser viewer) {
        Guards.require(viewer, Role.OBSERVABILITY_VIEWER, Role.ADMIN);
        try {
            return new MonitoringSnapshot(true, clock.instant(), monitoring.targets(), monitoring.alerts(), monitoring.indicators());
        } catch (MonitoringUnavailableException unavailable) {
            // The portal degrades gracefully: the page explains that Prometheus is not reachable.
            return new MonitoringSnapshot(false, clock.instant(), List.of(), List.of(), List.of());
        }
    }
}
