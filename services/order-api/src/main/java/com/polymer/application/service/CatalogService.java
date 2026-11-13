package com.polymer.application.service;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.CatalogUseCases;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.CatalogRepository;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.domain.catalog.CatalogPage;
import com.polymer.domain.catalog.CatalogQuery;
import com.polymer.domain.catalog.CatalogSearch;
import com.polymer.domain.catalog.Category;
import com.polymer.domain.catalog.PricedProduct;
import com.polymer.domain.catalog.Product;
import com.polymer.domain.catalog.ProductDetails;
import com.polymer.domain.identity.Role;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class CatalogService implements CatalogUseCases {
    private final CatalogRepository catalog;
    private final AuditLog audit;
    private final com.polymer.application.port.out.CatalogCache cache;
    private final PricingService pricing;
    private final CommerceOrderRepository sales;

    public CatalogService(CatalogRepository catalog, AuditLog audit) {
        this(catalog, audit, com.polymer.application.port.out.CatalogCache.NONE);
    }

    /** v5: public catalog reads go through a read-through cache; PostgreSQL remains the source of truth. */
    public CatalogService(CatalogRepository catalog, AuditLog audit, com.polymer.application.port.out.CatalogCache cache) {
        this(catalog, audit, cache, PricingService.none(Clock.systemUTC()), null);
    }

    /** v6: prices with running promotions; sales (optional) orders the store by popularity. */
    public CatalogService(CatalogRepository catalog, AuditLog audit, com.polymer.application.port.out.CatalogCache cache,
                          PricingService pricing, CommerceOrderRepository sales) {
        this.catalog = catalog;
        this.audit = audit;
        this.cache = cache;
        this.pricing = pricing;
        this.sales = sales;
    }

    /** Visible categories only: hidden (pre-v6) categories keep their data but are not shown in the store. */
    @Override
    public List<Category> categories() { return cache.categories(catalog::categories).stream().filter(Category::active).toList(); }

    @Override
    public List<Category> allCategories(AuthenticatedUser admin) {
        Guards.require(admin, Role.ADMIN);
        return catalog.categories();
    }

    @Override
    public CatalogPage search(CatalogQuery query) {
        if (!CatalogSearch.knownAttributes(query.attributes().keySet())) throw new IllegalArgumentException("Filtro técnico desconocido");
        Map<Long, Long> sold = query.sort() == CatalogQuery.Sort.POPULAR && sales != null ? sales.unitsSold() : Map.of();
        return CatalogSearch.search(storeProducts(), query, sold);
    }

    @Override
    public List<PricedProduct> pricedProducts(String query, Long categoryId) {
        return pricing.price(products(query, categoryId));
    }

    @Override
    public PricedProduct pricedProduct(long id) { return pricing.price(product(id)); }

    @Override
    public List<PricedProduct> related(long id, int limit) {
        Product product = product(id);
        return CatalogSearch.related(storeProducts(), product, Math.max(1, Math.min(limit, 12)));
    }

    /** Every active product of a visible category, priced now. The listing is cached; prices are not. */
    private List<PricedProduct> storeProducts() {
        Set<Long> visible = categories().stream().map(Category::id).collect(Collectors.toSet());
        List<Product> active = cache.activeProducts(null, () -> catalog.products(null, null, true));
        return pricing.price(active.stream().filter(p -> visible.contains(p.details().categoryId())).toList());
    }

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
        return createCategory(admin, slug, name, null);
    }

    @Override
    public Category createCategory(AuthenticatedUser admin, String slug, String name, Long parentId) {
        Guards.require(admin, Role.ADMIN);
        new Category(1, slug, name, parentId, 0, true); // validates
        List<Category> existing = catalog.categories();
        if (existing.stream().anyMatch(category -> category.slug().equals(slug))) {
            throw new BusinessConflictException("DUPLICATE_CATEGORY", "Ya existe una categoría con ese slug");
        }
        if (parentId != null && existing.stream().noneMatch(category -> category.id() == parentId && category.isMain())) {
            throw new IllegalArgumentException("parentId: debe ser una categoría principal existente");
        }
        Category created = catalog.createCategory(slug, name.strip(), parentId);
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
        audit.record(admin.username(), "PRODUCT_CREATED", "product:" + created.id(), details.sku() + " · S/ " + details.price());
        return created;
    }

    @Override
    public Product updateProduct(AuthenticatedUser admin, long id, ProductDetails details) {
        Guards.require(admin, Role.ADMIN);
        requireCategory(details.categoryId());
        if (catalog.skuExists(details.sku(), id)) throw new BusinessConflictException("DUPLICATE_SKU", "Ya existe un producto con ese SKU");
        Product updated = catalog.updateProduct(id, details).orElseThrow(() -> new ResourceNotFoundException("Producto no encontrado"));
        cache.invalidate();
        audit.record(admin.username(), "PRODUCT_UPDATED", "product:" + id, details.sku() + " · S/ " + details.price() + " · active=" + details.active());
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
