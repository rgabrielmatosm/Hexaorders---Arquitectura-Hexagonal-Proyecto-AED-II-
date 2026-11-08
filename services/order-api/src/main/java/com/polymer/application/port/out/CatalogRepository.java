package com.polymer.application.port.out;

import com.polymer.domain.catalog.Category;
import com.polymer.domain.catalog.Product;
import com.polymer.domain.catalog.ProductDetails;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface CatalogRepository {
    List<Category> categories();
    Optional<Category> category(long id);
    Category createCategory(String slug, String name);
    /** v6: creates a subcategory (parentId) or a main category (parentId null). */
    default Category createCategory(String slug, String name, Long parentId) {
        if (parentId != null) throw new UnsupportedOperationException("subcategories");
        return createCategory(slug, name);
    }
    List<Product> products(String query, Long categoryId, boolean onlyActive);
    Optional<Product> product(long id);
    Map<Long, Product> productsByIds(Collection<Long> ids);
    Product createProduct(ProductDetails details, int initialStock);
    Optional<Product> updateProduct(long id, ProductDetails details);
    /** Atomic: fails (empty) when the result would be negative. */
    Optional<Product> adjustStock(long id, int delta);
    List<Product> lowStock(int threshold);
    long countProducts();
    boolean skuExists(String sku, Long excludingId);
}
