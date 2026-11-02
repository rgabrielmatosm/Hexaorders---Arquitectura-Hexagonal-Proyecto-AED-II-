package com.hexaorders.application.port.out;

import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.Product;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Read-through cache for PUBLIC, non-sensitive catalog data. PostgreSQL stays the source of truth:
 * every method falls back to the loader on a miss or when the cache is unavailable.
 */
public interface CatalogCache {
    List<Category> categories(Supplier<List<Category>> loader);
    List<Product> activeProducts(Long categoryId, Supplier<List<Product>> loader);
    Optional<Product> product(long id, Supplier<Optional<Product>> loader);
    /** Called after any change of products, categories or stock. */
    void invalidate();

    /** No cache at all (tests, or when the cache is not deployed). */
    CatalogCache NONE = new CatalogCache() {
        @Override public List<Category> categories(Supplier<List<Category>> loader) { return loader.get(); }
        @Override public List<Product> activeProducts(Long categoryId, Supplier<List<Product>> loader) { return loader.get(); }
        @Override public Optional<Product> product(long id, Supplier<Optional<Product>> loader) { return loader.get(); }
        @Override public void invalidate() { }
    };
}
