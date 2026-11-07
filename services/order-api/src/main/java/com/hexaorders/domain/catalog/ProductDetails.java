package com.hexaorders.domain.catalog;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Editable attributes of a product, validated in business terms. Stock is managed separately.
 * v6 adds brand, model, a long description, the technical sheet (specs), the normalized filter
 * attributes (attributes: "ram" → "16 GB"), an optional previous price and a commercial status.
 * Prices are in PEN (soles).
 */
public record ProductDetails(String sku, String name, String description, long categoryId,
                             BigDecimal price, boolean active, int hue,
                             String brand, String model, String longDescription, List<Spec> specs,
                             Map<String, String> attributes, BigDecimal compareAtPrice, CommercialStatus status) {
    private static final Pattern SKU = Pattern.compile("[A-Z0-9-]{3,32}");
    private static final Pattern ATTRIBUTE_KEY = Pattern.compile("[a-z][a-z0-9_]{1,23}");
    private static final BigDecimal MAX_PRICE = new BigDecimal("100000.00");

    public ProductDetails {
        if (sku == null || !SKU.matcher(sku).matches()) {
            throw new IllegalArgumentException("sku: 3 a 32 mayúsculas, dígitos o guiones");
        }
        if (name == null || name.isBlank() || name.length() > 120) {
            throw new IllegalArgumentException("name: obligatorio, máximo 120 caracteres");
        }
        if (description == null || description.length() > 1000) {
            throw new IllegalArgumentException("description: máximo 1000 caracteres");
        }
        if (categoryId <= 0) {
            throw new IllegalArgumentException("categoryId: obligatorio");
        }
        if (price == null || price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0 || price.scale() > 2) {
            throw new IllegalArgumentException("price: mayor que 0, máximo 100000.00 y 2 decimales");
        }
        if (hue < 0 || hue > 359) {
            throw new IllegalArgumentException("hue: entre 0 y 359");
        }
        price = price.setScale(2);
        brand = brand == null || brand.isBlank() ? "Polymer" : brand.strip();
        if (brand.length() > 60) throw new IllegalArgumentException("brand: máximo 60 caracteres");
        model = model == null ? "" : model.strip();
        if (model.length() > 80) throw new IllegalArgumentException("model: máximo 80 caracteres");
        longDescription = longDescription == null ? "" : longDescription.strip();
        if (longDescription.length() > 4000) throw new IllegalArgumentException("longDescription: máximo 4000 caracteres");
        specs = specs == null ? List.of() : List.copyOf(specs);
        if (specs.size() > 40) throw new IllegalArgumentException("specs: máximo 40 filas");
        Map<String, String> cleanAttributes = new LinkedHashMap<>();
        if (attributes != null) {
            if (attributes.size() > 12) throw new IllegalArgumentException("attributes: máximo 12 filtros");
            attributes.forEach((key, value) -> {
                if (key == null || !ATTRIBUTE_KEY.matcher(key).matches()) throw new IllegalArgumentException("attributes: clave no válida (" + key + ")");
                if (value == null || value.isBlank() || value.length() > 40) throw new IllegalArgumentException("attributes: valor obligatorio, máximo 40 caracteres");
                cleanAttributes.put(key, value.strip());
            });
        }
        attributes = java.util.Collections.unmodifiableMap(cleanAttributes);
        if (compareAtPrice != null) {
            if (compareAtPrice.scale() > 2 || compareAtPrice.compareTo(MAX_PRICE) > 0) {
                throw new IllegalArgumentException("compareAtPrice: máximo 100000.00 y 2 decimales");
            }
            compareAtPrice = compareAtPrice.setScale(2);
            if (compareAtPrice.compareTo(price) <= 0) {
                throw new IllegalArgumentException("compareAtPrice: el precio anterior debe ser mayor que el precio actual");
            }
        }
        status = status == null ? CommercialStatus.STANDARD : status;
    }

    /** v2–v5 constructor: a product without the v6 commercial sheet. */
    public ProductDetails(String sku, String name, String description, long categoryId, BigDecimal price, boolean active, int hue) {
        this(sku, name, description, categoryId, price, active, hue, null, null, null, null, null, null, null);
    }
}
