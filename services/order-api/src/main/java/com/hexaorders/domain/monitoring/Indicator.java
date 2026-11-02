package com.hexaorders.domain.monitoring;

/** A named operational indicator. value is null when there is no data yet (for example, no traffic). */
public record Indicator(String key, String label, String unit, Double value) {}
