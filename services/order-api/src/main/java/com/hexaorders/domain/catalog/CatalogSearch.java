package com.hexaorders.domain.catalog;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Store search over the priced catalog: text search (name, brand, model, SKU, description), filters,
 * sorting, pagination and facets. Facets are computed from the products that match every OTHER
 * filter, so a value is offered only if choosing it returns results (no decorative filters).
 */
public final class CatalogSearch {
    /** Technical attributes offered as filters, in display order. */
    public static final Map<String, String> ATTRIBUTE_LABELS = attributeLabels();

    private CatalogSearch() { }

    private static Map<String, String> attributeLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("procesador", "Tipo de procesador");
        labels.put("ram", "Memoria RAM");
        labels.put("almacenamiento", "Almacenamiento");
        labels.put("gpu", "Marca de GPU");
        labels.put("pantalla", "Tamaño de pantalla");
        labels.put("resolucion", "Resolución");
        labels.put("frecuencia", "Frecuencia de actualización");
        labels.put("conectividad", "Conectividad");
        labels.put("bateria", "Batería");
        labels.put("capacidad", "Capacidad");
        labels.put("potencia", "Potencia");
        labels.put("formato", "Formato");
        return java.util.Collections.unmodifiableMap(labels);
    }

    public static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return decomposed.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9.+\" -]", " ").replaceAll("\\s+", " ").strip();
    }

    /**
     * @param catalog  active, priced products
     * @param unitsSold units sold per product id (for the "popular" order)
     */
    public static CatalogPage search(List<PricedProduct> catalog, CatalogQuery query, Map<Long, Long> unitsSold) {
        Objects.requireNonNull(query, "query");
        Map<Long, Long> sold = unitsSold == null ? Map.of() : unitsSold;
        List<String> terms = query.terms();
        Predicate<PricedProduct> text = p -> terms.isEmpty() || matchesAll(p.product(), terms);
        Predicate<PricedProduct> category = p -> query.categoryId() == null || p.product().belongsTo(query.categoryId());
        Predicate<PricedProduct> brand = p -> query.brands().isEmpty() || query.brands().contains(p.product().details().brand());
        Predicate<PricedProduct> price = p -> (query.minPrice() == null || p.price().price().compareTo(query.minPrice()) >= 0)
                && (query.maxPrice() == null || p.price().price().compareTo(query.maxPrice()) <= 0);
        Predicate<PricedProduct> available = p -> !query.onlyAvailable() || p.product().stock() > 0;
        Predicate<PricedProduct> offers = p -> !query.onlyOffers() || p.price().onSale();
        Predicate<PricedProduct> status = p -> query.status() == null || p.product().details().status() == query.status();
        Predicate<PricedProduct> base = text.and(price).and(available).and(offers).and(status);

        List<PricedProduct> matches = catalog.stream()
                .filter(base.and(category).and(brand).and(attributesMatch(query, null))).collect(Collectors.toList());
        matches.sort(comparator(query, terms, sold));

        int total = matches.size();
        int pages = Math.max(1, (total + query.size() - 1) / query.size());
        int page = Math.min(query.page(), pages);
        int from = Math.min(total, (page - 1) * query.size());
        List<PricedProduct> items = matches.subList(from, Math.min(total, from + query.size()));

        // Facets: each one ignores its own filter so the customer can switch between values.
        List<CatalogPage.Count> brands = counts(catalog.stream().filter(base.and(category).and(attributesMatch(query, null)))
                .map(p -> p.product().details().brand()).toList());
        // Categories: main categories when none is selected; subcategories of the selected main category otherwise.
        Long scope = query.categoryId() == null ? null : catalog.stream().map(PricedProduct::product)
                .filter(p -> p.belongsTo(query.categoryId())).map(Product::mainCategoryId).findFirst().orElse(null);
        Map<Long, String> names = new TreeMap<>();
        Map<Long, Integer> categoryCounts = new LinkedHashMap<>();
        catalog.stream().filter(base.and(brand).and(attributesMatch(query, null))).map(PricedProduct::product).forEach(p -> {
            if (scope != null && !Objects.equals(p.mainCategoryId(), scope)) return;
            long id = scope == null ? p.mainCategoryId() : p.details().categoryId();
            names.put(id, scope == null ? p.mainCategoryName() : p.categoryName());
            categoryCounts.merge(id, 1, Integer::sum);
        });
        List<CatalogPage.CategoryCount> categories = categoryCounts.entrySet().stream()
                .map(e -> new CatalogPage.CategoryCount(e.getKey(), names.get(e.getKey()), e.getValue()))
                .sorted(Comparator.comparing(CatalogPage.CategoryCount::name)).toList();
        List<CatalogPage.AttributeFacet> attributes = new ArrayList<>();
        ATTRIBUTE_LABELS.forEach((key, label) -> {
            List<CatalogPage.Count> values = counts(catalog.stream().filter(base.and(category).and(brand).and(attributesMatch(query, key)))
                    .map(p -> p.product().details().attributes().get(key)).filter(Objects::nonNull).toList());
            boolean selected = query.attributes().containsKey(key);
            if (values.size() >= 2 || selected && !values.isEmpty()) attributes.add(new CatalogPage.AttributeFacet(key, label, values));
        });
        BigDecimal min = matches.stream().map(p -> p.price().price()).min(BigDecimal::compareTo).orElse(null);
        BigDecimal max = matches.stream().map(p -> p.price().price()).max(BigDecimal::compareTo).orElse(null);
        int offerCount = (int) catalog.stream().filter(text.and(price).and(available).and(status).and(category).and(brand)
                .and(attributesMatch(query, null))).filter(p -> p.price().onSale()).count();
        int availableCount = (int) catalog.stream().filter(text.and(price).and(offers).and(status).and(category).and(brand)
                .and(attributesMatch(query, null))).filter(p -> p.product().stock() > 0).count();
        return new CatalogPage(items, total, page, query.size(), pages,
                new CatalogPage.Facets(brands, categories, attributes, min, max, offerCount, availableCount));
    }

    /** Related products: same subcategory first, then same main category; never the product itself. */
    public static List<PricedProduct> related(List<PricedProduct> catalog, Product product, int limit) {
        Comparator<PricedProduct> closeness = Comparator
                .comparing((PricedProduct p) -> p.product().details().categoryId() == product.details().categoryId() ? 0 : 1)
                .thenComparing(p -> p.product().details().brand().equals(product.details().brand()) ? 0 : 1)
                .thenComparing(p -> p.price().price().subtract(product.price()).abs());
        return catalog.stream()
                .filter(p -> p.product().id() != product.id() && p.product().stock() > 0)
                .filter(p -> Objects.equals(p.product().mainCategoryId(), product.mainCategoryId()))
                .sorted(closeness).limit(limit).toList();
    }

    private static Predicate<PricedProduct> attributesMatch(CatalogQuery query, String ignoredKey) {
        return p -> query.attributes().entrySet().stream().filter(e -> !e.getKey().equals(ignoredKey))
                .allMatch(e -> {
                    // Products without the attribute never match (and immutable sets reject contains(null)).
                    String value = p.product().details().attributes().get(e.getKey());
                    return value != null && e.getValue().contains(value);
                });
    }

    private static boolean matchesAll(Product product, List<String> terms) {
        String haystack = haystack(product);
        return terms.stream().allMatch(haystack::contains);
    }

    private static String haystack(Product product) {
        ProductDetails d = product.details();
        return normalize(String.join(" ", d.name(), d.brand(), d.model(), d.sku(), d.description(),
                product.categoryName() == null ? "" : product.categoryName(),
                product.mainCategoryName() == null ? "" : product.mainCategoryName()));
    }

    private static Comparator<PricedProduct> comparator(CatalogQuery query, List<String> terms, Map<Long, Long> sold) {
        Comparator<PricedProduct> byName = Comparator.comparing(p -> normalize(p.product().name()));
        Comparator<PricedProduct> newest = Comparator.comparing((PricedProduct p) ->
                p.product().createdAt() == null ? Instant.EPOCH : p.product().createdAt()).reversed()
                .thenComparing(Comparator.comparingLong((PricedProduct p) -> p.product().id()).reversed());
        return switch (query.sort()) {
            case PRICE_ASC -> Comparator.comparing((PricedProduct p) -> p.price().price()).thenComparing(byName);
            case PRICE_DESC -> Comparator.comparing((PricedProduct p) -> p.price().price()).reversed().thenComparing(byName);
            case NAME -> byName;
            case NEWEST -> newest;
            case POPULAR -> Comparator.comparingLong((PricedProduct p) -> -sold.getOrDefault(p.product().id(), 0L))
                    .thenComparing(p -> p.product().details().status() == CommercialStatus.BESTSELLER ? 0 : 1).thenComparing(byName);
            case DISCOUNT -> Comparator.comparingInt((PricedProduct p) -> -p.price().discountPercent()).thenComparing(byName);
            case RELEVANCE -> terms.isEmpty()
                    ? Comparator.comparing((PricedProduct p) -> p.product().stock() > 0 ? 0 : 1)
                        .thenComparing(p -> statusRank(p.product().details().status())).thenComparing(byName)
                    : Comparator.comparingInt((PricedProduct p) -> -relevance(p.product(), terms)).thenComparing(byName);
        };
    }

    private static int statusRank(CommercialStatus status) {
        return switch (status) { case FEATURED -> 0; case BESTSELLER -> 1; case NEW -> 2; case STANDARD -> 3; case CLEARANCE -> 4; };
    }

    /** Matches in SKU, model, brand and name weigh more than in the description. */
    private static int relevance(Product product, List<String> terms) {
        ProductDetails d = product.details();
        String sku = normalize(d.sku());
        String model = normalize(d.model());
        String brand = normalize(d.brand());
        String name = normalize(d.name());
        int score = 0;
        for (String term : terms) {
            if (sku.equals(term)) score += 100;
            if (model.contains(term)) score += 20;
            if (brand.equals(term)) score += 15;
            if (name.startsWith(term)) score += 12;
            if (name.contains(term)) score += 8;
        }
        return score + (product.stock() > 0 ? 1 : 0);
    }

    private static List<CatalogPage.Count> counts(List<String> values) {
        Map<String, Integer> counts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        values.forEach(value -> counts.merge(value, 1, Integer::sum));
        List<CatalogPage.Count> result = new ArrayList<>();
        counts.forEach((value, count) -> result.add(new CatalogPage.Count(value, count)));
        result.sort(Comparator.comparing(CatalogPage.Count::value, CatalogSearch::naturalOrder));
        return result;
    }

    /** "8 GB" < "16 GB" < "1 TB": numbers compare by magnitude when units are known. */
    static int naturalOrder(String a, String b) {
        double x = magnitude(a);
        double y = magnitude(b);
        if (!Double.isNaN(x) && !Double.isNaN(y) && x != y) return Double.compare(x, y);
        return a.compareToIgnoreCase(b);
    }

    private static double magnitude(String value) {
        var matcher = java.util.regex.Pattern.compile("^([0-9]+(?:[.,][0-9]+)?)\\s*(tb|gb|mb|hz|mah|w|\"|pulgadas|mp|m|k)?", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(value.strip());
        if (!matcher.find()) return Double.NaN;
        double number = Double.parseDouble(matcher.group(1).replace(',', '.'));
        String unit = matcher.group(2) == null ? "" : matcher.group(2).toLowerCase(Locale.ROOT);
        return switch (unit) { case "tb" -> number * 1024; case "mb" -> number / 1024; default -> number; };
    }

    /** Whether a set of attribute keys only uses keys offered as filters. */
    public static boolean knownAttributes(Set<String> keys) { return ATTRIBUTE_LABELS.keySet().containsAll(keys); }
}
