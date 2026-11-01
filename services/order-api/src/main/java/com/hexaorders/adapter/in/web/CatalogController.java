package com.hexaorders.adapter.in.web;

import com.hexaorders.application.port.in.CatalogUseCases;
import com.hexaorders.domain.catalog.Category;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public catalog (read only). */
@RestController
@RequestMapping("/api/catalog")
public final class CatalogController {
    private final CatalogUseCases catalog;

    public CatalogController(CatalogUseCases catalog) { this.catalog = catalog; }

    @GetMapping("/categories")
    public List<Category> categories() { return catalog.categories(); }

    @GetMapping("/products")
    public List<ProductView> products(@RequestParam(required = false) String q, @RequestParam(required = false) Long category) {
        return catalog.products(q, category).stream().map(ProductView::of).toList();
    }

    @GetMapping("/products/{id}")
    public ProductView product(@PathVariable long id) { return ProductView.of(catalog.product(id)); }
}
