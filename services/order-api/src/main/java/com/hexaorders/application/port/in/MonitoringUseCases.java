package com.hexaorders.application.port.in;

import com.hexaorders.domain.monitoring.MonitoringSnapshot;

/** Monitoring portal (OBSERVABILITY_VIEWER and ADMIN). Never fails because monitoring is down. */
public interface MonitoringUseCases {
    MonitoringSnapshot snapshot(AuthenticatedUser viewer);
}
