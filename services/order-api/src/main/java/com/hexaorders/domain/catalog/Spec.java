package com.hexaorders.domain.catalog;

/** One row of a product's technical specification sheet ("Procesador" → "Intel Core i7-13620H"). */
public record Spec(String label, String value) {
    public Spec {
        if (label == null || label.isBlank() || label.length() > 60) throw new IllegalArgumentException("spec: etiqueta obligatoria, máximo 60 caracteres");
        if (value == null || value.isBlank() || value.length() > 200) throw new IllegalArgumentException("spec: valor obligatorio, máximo 200 caracteres");
        label = label.strip();
        value = value.strip();
    }
}
