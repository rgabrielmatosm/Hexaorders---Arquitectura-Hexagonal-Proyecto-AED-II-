package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.AdministrationUseCases;
import com.polymer.application.port.in.MonitoringUseCases;
import com.polymer.application.port.in.SystemHealth;
import com.polymer.domain.monitoring.MonitoringSnapshot;
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
