package com.hexaorders.application.service;

import com.hexaorders.application.exception.BusinessConflictException;
import com.hexaorders.application.exception.ResourceNotFoundException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.CatalogUseCases;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.ProductDetails;
import com.hexaorders.domain.identity.Role;
import java.util.List;

public final class CatalogService implements CatalogUseCases {
    private final CatalogRepository catalog;
    private final AuditLog audit;
    private final com.hexaorders.application.port.out.CatalogCache cache;

    public CatalogService(CatalogRepository catalog, AuditLog audit) {
        this(catalog, audit, com.hexaorders.application.port.out.CatalogCache.NONE);
    }

    /** v5: public catalog reads go through a read-through cache; PostgreSQL remains the source of truth. */
    public CatalogService(CatalogRepository catalog, AuditLog audit, com.hexaorders.application.port.out.CatalogCache cache) {
        this.catalog = catalog;
        this.audit = audit;
        this.cache = cache;
    }

    @Override
    public List<Category> categories() { return cache.categories(catalog::categories); }

    @Override
    public List<Product> products(String query, Long categoryId) {
        String clean = Guards.text(query, 80, "q");
        // Free-text searches are not cached (unbounded keys); category listings are.
        if (clean != null) return catalog.products(clean, categoryId, true);
        return cache.activeProducts(categoryId, () -> catalog.products(null, categoryId, true));
    }

    @Override
    public Product product(long id) {
        return cache.product(id, () -> catalog.product(id)).filter(product -> product.details().active())
                .orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
    }

    @Override
    public List<Product> allProducts(AuthenticatedUser admin) {
        Guards.require(admin, Role.ADMIN);
        return catalog.products(null, null, false);
    }

    @Override
    public Category createCategory(AuthenticatedUser admin, String slug, String name) {
        Guards.require(admin, Role.ADMIN);
        new Category(1, slug, name); // validates
        if (catalog.categories().stream().anyMatch(category -> category.slug().equals(slug))) {
            throw new BusinessConflictException("DUPLICATE_CATEGORY", "Ya existe una categoría con ese slug");
        }
        Category created = catalog.createCategory(slug, name.strip());
        cache.invalidate();
        audit.record(admin.username(), "CATEGORY_CREATED", "category:" + created.id(), slug);
        return created;
    }

    @Override
    public Product createProduct(AuthenticatedUser admin, ProductDetails details, int initialStock) {
        Guards.require(admin, Role.ADMIN);
        if (initialStock < 0 || initialStock > 100000) throw new IllegalArgumentException("stock: entre 0 y 100000");
        requireCategory(details.categoryId());
        if (catalog.skuExists(details.sku(), null)) throw new BusinessConflictException("DUPLICATE_SKU", "Ya existe un producto con ese SKU");
        Product created = catalog.createProduct(details, initialStock);
        cache.invalidate();
        audit.record(admin.username(), "PRODUCT_CREATED", "product:" + created.id(), details.sku() + " · " + details.price() + " USD");
        return created;
    }

    @Override
    public Product updateProduct(AuthenticatedUser admin, long id, ProductDetails details) {
        Guards.require(admin, Role.ADMIN);
        requireCategory(details.categoryId());
        if (catalog.skuExists(details.sku(), id)) throw new BusinessConflictException("DUPLICATE_SKU", "Ya existe un producto con ese SKU");
        Product updated = catalog.updateProduct(id, details).orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        cache.invalidate();
        audit.record(admin.username(), "PRODUCT_UPDATED", "product:" + id, details.sku() + " · " + details.price() + " USD · active=" + details.active());
        return updated;
    }

    @Override
    public Product adjustStock(AuthenticatedUser admin, long id, int delta, String reason) {
        Guards.require(admin, Role.ADMIN);
        String cleanReason = Guards.text(reason, 200, "reason");
        if (delta == 0 || Math.abs(delta) > 100000) throw new IllegalArgumentException("delta: distinto de 0 y como máximo ±100000");
        if (cleanReason == null) throw new IllegalArgumentException("reason: obligatorio");
        catalog.product(id).orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        Product updated = catalog.adjustStock(id, delta)
                .orElseThrow(() -> new BusinessConflictException("NEGATIVE_STOCK", "El ajuste dejaría el stock en negativo"));
        cache.invalidate();
        audit.record(admin.username(), "STOCK_ADJUSTED", "product:" + id, (delta > 0 ? "+" : "") + delta + " · " + cleanReason);
        return updated;
    }

    private void requireCategory(long categoryId) {
        catalog.category(categoryId).orElseThrow(() -> new IllegalArgumentException("categoryId: la categoría no existe"));
    }
}
