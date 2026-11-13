package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.OperationsUseCases;
import com.polymer.domain.incident.Incident;
import com.polymer.domain.incident.IncidentCategory;
import com.polymer.domain.incident.IncidentStatus;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public final class IncidentController {
    private final OperationsUseCases operations;

    public IncidentController(OperationsUseCases operations) { this.operations = operations; }

    @GetMapping
    public List<Incident> list(@RequestParam(required = false) IncidentStatus status) {
        return operations.incidents(CurrentUser.require(), status);
    }

    @PostMapping
    public ResponseEntity<Incident> report(@RequestBody IncidentRequest request) {
        Incident created = operations.reportIncident(CurrentUser.require(), request.orderId(), request.category(), request.description());
        return ResponseEntity.created(URI.create("/api/incidents/" + created.id())).body(created);
    }

    @PostMapping("/{id}/resolve")
    public Incident resolve(@PathVariable long id, @RequestBody ResolveRequest request) {
        return operations.resolveIncident(CurrentUser.require(), id, request.resolution());
    }

    public record IncidentRequest(UUID orderId, IncidentCategory category, String description) {}
    public record ResolveRequest(String resolution) {}
}
