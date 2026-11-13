package com.polymer.domain.monitoring;

import java.time.Instant;

/** A scrape target as reported by the monitoring system (job, instance and health). */
public record ServiceTarget(String job, String instance, String health, Instant lastScrape, double scrapeSeconds, String lastError) {}
