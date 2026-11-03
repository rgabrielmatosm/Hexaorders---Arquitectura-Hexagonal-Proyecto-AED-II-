package com.hexaorders.domain.catalog;

/** Commercial label of a product in the store. It never changes price or stock by itself. */
public enum CommercialStatus {
    STANDARD("Estándar"), NEW("Nuevo"), FEATURED("Destacado"), BESTSELLER("Más vendido"), CLEARANCE("Liquidación");

    private final String label;

    CommercialStatus(String label) { this.label = label; }

    public String label() { return label; }
}
