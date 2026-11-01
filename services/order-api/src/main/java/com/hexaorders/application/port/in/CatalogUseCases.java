package com.hexaorders.application.port.in;

import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.ProductDetails;
import java.util.List;

public interface CatalogUseCases {
    List<Category> categories();
    /** Active products, optionally filtered by text and category (public catalog). */
    List<Product> products(String query, Long categoryId);
    Product product(long id);
    /** Includes inactive products (administration). */
    List<Product> allProducts(AuthenticatedUser admin);
    Category createCategory(AuthenticatedUser admin, String slug, String name);
    Product createProduct(AuthenticatedUser admin, ProductDetails details, int initialStock);
    Product updateProduct(AuthenticatedUser admin, long id, ProductDetails details);
    Product adjustStock(AuthenticatedUser admin, long id, int delta, String reason);
}
