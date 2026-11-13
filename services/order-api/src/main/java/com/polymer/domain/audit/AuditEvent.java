package com.polymer.domain.audit;

import java.time.Instant;

public record AuditEvent(long id, String actor, String action, String target, String details, Instant at) {}
