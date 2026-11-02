package com.hexaorders.domain.catalog;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Editable attributes of a product, validated in business terms. Stock is managed separately. */
public record ProductDetails(String sku, String name, String description, long categoryId,
                             BigDecimal price, boolean active, int hue) {
    private static final Pattern SKU = Pattern.compile("[A-Z0-9-]{3,32}");
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
    }
}
