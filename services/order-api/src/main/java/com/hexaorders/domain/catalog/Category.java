package com.hexaorders.domain.catalog;

import java.util.regex.Pattern;

public record Category(long id, String slug, String name) {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public Category {
        if (slug == null || slug.length() > 48 || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug: minúsculas, dígitos y guiones (máximo 48)");
        }
        if (name == null || name.isBlank() || name.length() > 60) {
            throw new IllegalArgumentException("name: obligatorio, máximo 60 caracteres");
        }
    }
}
