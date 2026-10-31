package com.hexaorders.domain;

public enum OrderStatus {
    APPROVED, REVIEW;

    public static OrderStatus fromRiskScore(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("El puntaje debe estar entre 0 y 100");
        }
        return score >= 70 ? REVIEW : APPROVED;
    }
}
