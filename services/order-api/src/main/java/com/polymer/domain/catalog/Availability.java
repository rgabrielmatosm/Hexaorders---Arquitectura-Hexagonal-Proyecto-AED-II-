package com.polymer.domain.catalog;

/** Availability shown to customers, derived from stock and the active flag. */
public enum Availability {
    IN_STOCK("En stock"), LOW_STOCK("Últimas unidades"), OUT_OF_STOCK("Agotado"), UNAVAILABLE("No disponible");

    public static final int LOW_STOCK_THRESHOLD = 5;
    private final String label;

    Availability(String label) { this.label = label; }

    public String label() { return label; }

    public static Availability of(boolean active, int stock) {
        if (!active) return UNAVAILABLE;
        if (stock <= 0) return OUT_OF_STOCK;
        return stock <= LOW_STOCK_THRESHOLD ? LOW_STOCK : IN_STOCK;
    }
}
