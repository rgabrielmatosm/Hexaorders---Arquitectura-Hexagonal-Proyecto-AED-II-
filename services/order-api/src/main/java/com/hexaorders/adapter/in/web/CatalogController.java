package com.hexaorders.adapter.in.web;

import com.hexaorders.application.port.in.CatalogUseCases;
import com.hexaorders.application.port.in.PromotionUseCases;
import com.hexaorders.domain.catalog.CatalogPage;
import com.hexaorders.domain.catalog.CatalogQuery;
import com.hexaorders.domain.catalog.CatalogSearch;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.CommercialStatus;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.MultiValueMap;
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
    private final PromotionUseCases promotions;

    public CatalogController(CatalogUseCases catalog, PromotionUseCases promotions) {
        this.catalog = catalog;
        this.promotions = promotions;
    }

    @GetMapping("/categories")
    public List<Category> categories() { return catalog.categories(); }

    /** v2–v5 contract: every active product (now with the store price), optionally filtered. */
    @GetMapping("/products")
    public List<ProductView> products(@RequestParam(required = false) String q, @RequestParam(required = false) Long category) {
        return catalog.pricedProducts(q, category).stream().map(ProductView::of).toList();
    }

    @GetMapping("/products/{id}")
    public ProductView product(@PathVariable long id) { return ProductView.of(catalog.pricedProduct(id)); }

    @GetMapping("/products/{id}/related")
    public List<ProductView> related(@PathVariable long id, @RequestParam(defaultValue = "8") int limit) {
        return catalog.related(id, limit).stream().map(ProductView::of).toList();
    }

    /**
     * Store search. Parameters: q, category, brand (repeatable or comma separated), min, max, available,
     * offers, status, sort (relevance, price_asc, price_desc, name, newest, popular, discount), page, size
     * and technical filters as attr.KEY=VALUE (for example attr.ram=16 GB).
     */
    @GetMapping("/search")
    public SearchResponse search(@RequestParam MultiValueMap<String, String> params) {
        Map<String, Set<String>> attributes = new LinkedHashMap<>();
        params.forEach((key, values) -> {
            if (key.startsWith("attr.")) attributes.put(key.substring(5), split(values));
        });
        CatalogQuery query = new CatalogQuery(params.getFirst("q"), longParam(params.getFirst("category"), "category"),
                split(params.get("brand")), decimal(params.getFirst("min"), "min"), decimal(params.getFirst("max"), "max"),
                flag(params.getFirst("available")), flag(params.getFirst("offers")), status(params.getFirst("status")), attributes,
                CatalogQuery.Sort.parse(params.getFirst("sort")), intParam(params.getFirst("page"), 1, "page"),
                intParam(params.getFirst("size"), CatalogQuery.DEFAULT_SIZE, "size"));
        CatalogPage page = catalog.search(query);
        return new SearchResponse(page.items().stream().map(ProductView::of).toList(), page.total(), page.page(), page.size(),
                page.pages(), page.facets(), CatalogSearch.ATTRIBUTE_LABELS);
    }

    /** Running campaigns (name, discount, dates and how many products they discount). */
    @GetMapping("/promotions")
    public List<PromotionView> promotions() { return promotions.current().stream().map(PromotionView::of).toList(); }

    public record SearchResponse(List<ProductView> items, int total, int page, int size, int pages, CatalogPage.Facets facets,
                                 Map<String, String> attributeLabels) {}

    private static Set<String> split(List<String> values) {
        if (values == null) return Set.of();
        return values.stream().flatMap(v -> Arrays.stream(v.split(","))).map(String::strip).filter(v -> !v.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static boolean flag(String value) { return value != null && (value.equals("1") || value.equalsIgnoreCase("true")); }

    private static Long longParam(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try { return Long.parseLong(value.strip()); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + ": número no válido"); }
    }

    private static int intParam(String value, int fallback, String name) {
        if (value == null || value.isBlank()) return fallback;
        try { return Integer.parseInt(value.strip()); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + ": número no válido"); }
    }

    private static BigDecimal decimal(String value, String name) {
        if (value == null || value.isBlank()) return null;
        try { return new BigDecimal(value.strip()); } catch (NumberFormatException e) { throw new IllegalArgumentException(name + ": importe no válido"); }
    }

    private static CommercialStatus status(String value) {
        if (value == null || value.isBlank()) return null;
        try { return CommercialStatus.valueOf(value.strip().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("status: STANDARD, NEW, FEATURED, BESTSELLER o CLEARANCE"); }
    }
}
