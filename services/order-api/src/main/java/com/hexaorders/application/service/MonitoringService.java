package com.hexaorders.application.service;

import com.hexaorders.application.exception.MonitoringUnavailableException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.MonitoringUseCases;
import com.hexaorders.application.port.out.MonitoringPort;
import com.hexaorders.domain.identity.Role;
import com.hexaorders.domain.monitoring.MonitoringSnapshot;
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
