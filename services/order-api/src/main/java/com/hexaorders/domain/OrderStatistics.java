package com.hexaorders.domain;

/** Aggregated counts of persisted orders, expressed in the business language. */
public record OrderStatistics(long total, long approved, long review) {
    public OrderStatistics {
        if (approved < 0 || review < 0) {
            throw new IllegalArgumentException("Los contadores no pueden ser negativos");
        }
        if (total != approved + review) {
            throw new IllegalArgumentException("El total debe ser la suma de pedidos aprobados y en revisión");
        }
    }

    public static OrderStatistics of(long approved, long review) {
        return new OrderStatistics(approved + review, approved, review);
    }
}
