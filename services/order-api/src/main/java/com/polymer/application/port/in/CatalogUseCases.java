package com.polymer.application.port.in;

import com.polymer.domain.catalog.CatalogPage;
import com.polymer.domain.catalog.CatalogQuery;
import com.polymer.domain.catalog.Category;
import com.polymer.domain.catalog.PricedProduct;
import com.polymer.domain.catalog.Product;
import com.polymer.domain.catalog.ProductDetails;
import java.util.List;

public interface CatalogUseCases {
    /** Visible categories of the store (main categories and subcategories). */
    List<Category> categories();
    /** Active products, optionally filtered by text and category (public catalog, regular prices). */
    List<Product> products(String query, Long categoryId);
    Product product(long id);
    /** Includes inactive products (administration). */
    List<Product> allProducts(AuthenticatedUser admin);
    Category createCategory(AuthenticatedUser admin, String slug, String name);
    Product createProduct(AuthenticatedUser admin, ProductDetails details, int initialStock);
    Product updateProduct(AuthenticatedUser admin, long id, ProductDetails details);
    Product adjustStock(AuthenticatedUser admin, long id, int delta, String reason);

    // ---- v6: store
    /** Search, filters, sorting, pagination and facets over the priced catalog. */
    CatalogPage search(CatalogQuery query);
    /** Active products with the price the customer pays now (promotions applied). */
    List<PricedProduct> pricedProducts(String query, Long categoryId);
    PricedProduct pricedProduct(long id);
    List<PricedProduct> related(long id, int limit);
    /** Every category, including hidden ones (administration). */
    List<Category> allCategories(AuthenticatedUser admin);
    /** Creates a subcategory when parentId is a main category, or a main category when parentId is null. */
    Category createCategory(AuthenticatedUser admin, String slug, String name, Long parentId);
}
