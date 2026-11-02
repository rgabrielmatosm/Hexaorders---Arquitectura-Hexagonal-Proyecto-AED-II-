package com.hexaorders.domain.monitoring;

import java.time.Instant;

/** An alerting rule and its current state: inactive, pending or firing. */
public record AlertRule(String group, String name, String state, String severity, String summary, Instant activeSince) {}
