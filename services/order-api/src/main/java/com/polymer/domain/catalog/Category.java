package com.polymer.domain.catalog;

import java.util.regex.Pattern;

/**
 * Catalog category. v6: categories form a two-level tree (main category → subcategory); products
 * belong to a subcategory. parentId is null for main categories. Inactive categories are kept
 * (with their products and orders) but hidden from the store.
 */
public record Category(long id, String slug, String name, Long parentId, int position, Boolean active) {
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public Category {
        if (slug == null || slug.length() > 48 || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("slug: minúsculas, dígitos y guiones (máximo 48)");
        }
        if (name == null || name.isBlank() || name.length() > 80) {
            throw new IllegalArgumentException("name: obligatorio, máximo 80 caracteres");
        }
        if (parentId != null && parentId == id) throw new IllegalArgumentException("parentId: una categoría no puede ser su propio padre");
        if (position < 0) throw new IllegalArgumentException("position: no puede ser negativa");
        // Data written before v6 (cache, older clients) has no "active": those categories were visible.
        active = active == null || active;
    }

    /** v2–v5 constructor: a visible main category. */
    public Category(long id, String slug, String name) { this(id, slug, name, null, 0, true); }

    public boolean isMain() { return parentId == null; }
}
