package com.hexaorders.adapter.in.web;

import com.hexaorders.adapter.in.web.security.CurrentUser;
import com.hexaorders.application.port.in.AdministrationUseCases;
import com.hexaorders.application.port.in.SystemHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** OBSERVABILITY_VIEWER and ADMIN: read-only technical status; cannot modify business data. */
@RestController
@RequestMapping("/api/ops")
public final class OpsController {
    private final AdministrationUseCases administration;

    public OpsController(AdministrationUseCases administration) { this.administration = administration; }

    @GetMapping("/health")
    public SystemHealth health() { return administration.health(CurrentUser.require()); }
}
