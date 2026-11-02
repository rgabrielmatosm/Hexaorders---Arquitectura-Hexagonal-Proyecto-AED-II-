package com.hexaorders.adapter.in.web;

import com.hexaorders.adapter.in.web.security.CurrentUser;
import com.hexaorders.application.port.in.AdministrationUseCases;
import com.hexaorders.application.port.in.MonitoringUseCases;
import com.hexaorders.application.port.in.SystemHealth;
import com.hexaorders.domain.monitoring.MonitoringSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OBSERVABILITY_VIEWER and ADMIN: read-only technical status; cannot modify business data. */
@RestController
@RequestMapping("/api/ops")
public final class OpsController {
    private final AdministrationUseCases administration;
    private final MonitoringUseCases monitoring;

    public OpsController(AdministrationUseCases administration, MonitoringUseCases monitoring) {
        this.administration = administration;
        this.monitoring = monitoring;
    }

    /** Targets, alert rules and named indicators read from Prometheus inside the Docker network. */
    @GetMapping("/monitoring")
    public MonitoringSnapshot monitoring() { return monitoring.snapshot(CurrentUser.require()); }

    @GetMapping("/health")
    public SystemHealth health() { return administration.health(CurrentUser.require()); }
}
