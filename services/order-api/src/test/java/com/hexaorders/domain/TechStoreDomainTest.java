package com.hexaorders.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hexaorders.domain.catalog.Availability;
import com.hexaorders.domain.catalog.CatalogPage;
import com.hexaorders.domain.catalog.CatalogQuery;
import com.hexaorders.domain.catalog.CatalogSearch;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.CommercialStatus;
import com.hexaorders.domain.catalog.PriceTag;
import com.hexaorders.domain.catalog.PricedProduct;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.ProductDetails;
import com.hexaorders.domain.catalog.Promotion;
import com.hexaorders.domain.catalog.Spec;
import com.hexaorders.domain.fulfillment.CartItem;
import com.hexaorders.domain.fulfillment.CheckoutSummary;
import com.hexaorders.domain.fulfillment.DeliveryMethod;
import com.hexaorders.domain.fulfillment.PaymentMethod;
import com.hexaorders.domain.fulfillment.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** v6 store rules: pricing with promotions, search and facets, checkout methods. */
class TechStoreDomainTest {
    static final Instant NOW = Instant.parse("2026-11-03T12:00:00Z");
    static final long LAPTOPS = 10;     // main category
    static final long GAMING_LAPTOPS = 11;
    static final long OFFICE_LAPTOPS = 12;
    static final long MONITORS = 20;

    static Product product(long id, String name, String brand, String model, long sub, long main, String price, String compareAt,
                           int stock, CommercialStatus status, Map<String, String> attributes, Instant created) {
        ProductDetails details = new ProductDetails("SKU-" + id, name, "", sub, new BigDecimal(price), true, 200, brand, model,
                "", List.of(new Spec("Marca", brand)), attributes, compareAt == null ? null : new BigDecimal(compareAt), status);
        return new Product(id, details, "Sub " + sub, stock, main, "Main " + main, created, created);
    }

    static final Product LOQ = product(1, "Laptop gaming Lenovo LOQ 15IRX9", "Lenovo", "LOQ 15IRX9", GAMING_LAPTOPS, LAPTOPS, "4599.00", null, 10,
            CommercialStatus.STANDARD, Map.of("ram", "16 GB", "gpu", "NVIDIA"), Instant.parse("2026-05-01T00:00:00Z"));
    static final Product TUF = product(2, "Laptop gaming ASUS TUF Gaming A15", "ASUS", "TUF Gaming A15 FA507NV", GAMING_LAPTOPS, LAPTOPS, "4299.00", "4799.00", 4,
            CommercialStatus.NEW, Map.of("ram", "16 GB", "gpu", "NVIDIA"), Instant.parse("2026-10-20T00:00:00Z"));
    static final Product IDEAPAD = product(3, "Laptop Lenovo IdeaPad Slim 3", "Lenovo", "IdeaPad Slim 3 15IRU9", OFFICE_LAPTOPS, LAPTOPS, "2299.00", null, 0,
            CommercialStatus.STANDARD, Map.of("ram", "8 GB", "gpu", "Intel"), Instant.parse("2026-06-01T00:00:00Z"));
    static final Product MONITOR = product(4, "Monitor LG 27GR95QE", "LG", "UltraGear 27GR95QE-B", 21, MONITORS, "3299.00", null, 30,
            CommercialStatus.FEATURED, Map.of("resolucion", "QHD"), Instant.parse("2026-07-01T00:00:00Z"));

    static Promotion promo(long id, int percent, Set<Long> categories, Set<Long> products, String starts, String ends) {
        return new Promotion(id, "promo-" + id, "Promo " + id, "", percent, Instant.parse(starts), Instant.parse(ends), true, categories, products);
    }

    static final Promotion GAMING_WEEK = promo(1, 15, Set.of(GAMING_LAPTOPS), Set.of(), "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z");
    static final Promotion LAPTOP_DAYS = promo(2, 10, Set.of(LAPTOPS), Set.of(), "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z");
    static final Promotion EXPIRED = promo(3, 50, Set.of(MONITORS), Set.of(), "2026-09-01T00:00:00Z", "2026-10-01T00:00:00Z");

    static List<PricedProduct> priced(List<Product> products, List<Promotion> promotions) {
        return products.stream().map(p -> new PricedProduct(p, PriceTag.of(p, promotions, NOW))).toList();
    }

    @Test
    void theBestRunningPromotionAppliesWithoutStackingAndTheDiscountIsComputed() {
        PriceTag tag = PriceTag.of(LOQ, List.of(GAMING_WEEK, LAPTOP_DAYS), NOW);
        assertThat(tag.price()).isEqualByComparingTo("3909.15"); // 4599.00 × 0.85, half up
        assertThat(tag.previousPrice()).isEqualByComparingTo("4599.00");
        assertThat(tag.discountPercent()).isEqualTo(15);
        assertThat(tag.promotionName()).isEqualTo("Promo 1");
        // A main-category promotion also reaches its subcategories.
        assertThat(PriceTag.of(IDEAPAD, List.of(GAMING_WEEK, LAPTOP_DAYS), NOW).price()).isEqualByComparingTo("2069.10");
    }

    @Test
    void thePreviousPriceIsShownWhenItIsHigherAndExpiredPromotionsAreIgnored() {
        PriceTag tuf = PriceTag.of(TUF, List.of(GAMING_WEEK), NOW);
        assertThat(tuf.price()).isEqualByComparingTo("3654.15");
        assertThat(tuf.previousPrice()).isEqualByComparingTo("4799.00");
        assertThat(tuf.discountPercent()).isEqualTo(24);
        PriceTag monitor = PriceTag.of(MONITOR, List.of(EXPIRED), NOW);
        assertThat(monitor.price()).isEqualByComparingTo("3299.00");
        assertThat(monitor.onSale()).isFalse();
        assertThat(EXPIRED.stateAt(NOW)).isEqualTo(Promotion.PromotionState.EXPIRED);
        assertThat(promo(9, 5, Set.of(MONITORS), Set.of(), "2026-12-01T00:00:00Z", "2026-12-10T00:00:00Z").stateAt(NOW))
                .isEqualTo(Promotion.PromotionState.SCHEDULED);
    }

    @Test
    void invalidPromotionsAndProductSheetsAreRejected() {
        assertThatThrownBy(() -> promo(5, 95, Set.of(1L), Set.of(), "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z"))
                .hasMessageContaining("discountPercent");
        assertThatThrownBy(() -> promo(5, 10, Set.of(1L), Set.of(), "2026-12-01T00:00:00Z", "2026-11-01T00:00:00Z"))
                .hasMessageContaining("endsAt");
        assertThatThrownBy(() -> promo(5, 10, Set.of(), Set.of(), "2026-11-01T00:00:00Z", "2026-12-01T00:00:00Z"))
                .hasMessageContaining("al menos");
        assertThatThrownBy(() -> product(9, "X", "B", "M", 1, 1, "100.00", "90.00", 1, null, Map.of(), NOW))
                .hasMessageContaining("precio anterior");
        assertThatThrownBy(() -> product(9, "X", "B", "M", 1, 1, "100.00", null, 1, null, Map.of("RAM!", "8"), NOW))
                .hasMessageContaining("attributes");
        // Data written before v6 keeps working: visible category, standard product, regular price.
        assertThat(new Category(1, "audio", "Audio").active()).isTrue();
        assertThat(new ProductDetails("OLD-1", "Old", "", 1, BigDecimal.TEN, true, 10).status()).isEqualTo(CommercialStatus.STANDARD);
        assertThat(Availability.of(true, 3)).isEqualTo(Availability.LOW_STOCK);
        assertThat(Availability.of(true, 0)).isEqualTo(Availability.OUT_OF_STOCK);
    }

    @Test
    void textSearchMatchesNameBrandModelAndSkuIgnoringAccentsAndCase() {
        List<PricedProduct> catalog = priced(List.of(LOQ, TUF, IDEAPAD, MONITOR), List.of());
        assertThat(ids(CatalogSearch.search(catalog, query("lenovo", null), Map.of()))).containsExactlyInAnyOrder(1L, 3L);
        assertThat(ids(CatalogSearch.search(catalog, query("FA507NV", null), Map.of()))).containsExactly(2L);
        assertThat(ids(CatalogSearch.search(catalog, query("sku-4", null), Map.of()))).containsExactly(4L);
        assertThat(ids(CatalogSearch.search(catalog, query("LÁPTOP GAMING", null), Map.of()))).containsExactlyInAnyOrder(1L, 2L);
        assertThat(CatalogSearch.search(catalog, query("tostadora", null), Map.of()).total()).isZero();
    }

    @Test
    void filtersCombineAndFacetsOnlyOfferValuesThatReturnResults() {
        List<PricedProduct> catalog = priced(List.of(LOQ, TUF, IDEAPAD, MONITOR), List.of(GAMING_WEEK));
        CatalogQuery inLaptops = new CatalogQuery(null, LAPTOPS, Set.of("Lenovo"), null, null, false, false, null,
                Map.of("ram", Set.of("16 GB")), CatalogQuery.Sort.RELEVANCE, 1, 24);
        CatalogPage page = CatalogSearch.search(catalog, inLaptops, Map.of());
        assertThat(ids(page)).containsExactly(1L);
        // Brand facet ignores the brand filter (ASUS can still be chosen); RAM facet ignores the RAM filter.
        assertThat(page.facets().brands()).extracting(CatalogPage.Count::value).containsExactlyInAnyOrder("ASUS", "Lenovo");
        assertThat(page.facets().attributes()).filteredOn(f -> f.key().equals("ram")).singleElement()
                .satisfies(f -> assertThat(f.values()).extracting(CatalogPage.Count::value).containsExactly("8 GB", "16 GB"));
        // Inside a main category the category facet lists its subcategories that still have results:
        // with "16 GB" selected the office subcategory (8 GB only) is not offered.
        assertThat(page.facets().categories()).extracting(CatalogPage.CategoryCount::id).containsExactly(GAMING_LAPTOPS);
        CatalogQuery allLaptops = new CatalogQuery(null, LAPTOPS, null, null, null, false, false, null, null, CatalogQuery.Sort.RELEVANCE, 1, 24);
        assertThat(CatalogSearch.search(catalog, allLaptops, Map.of()).facets().categories())
                .extracting(CatalogPage.CategoryCount::id).containsExactlyInAnyOrder(GAMING_LAPTOPS, OFFICE_LAPTOPS);

        // MONITOR has no "ram": a technical filter excludes it instead of failing.
        CatalogQuery anyRam = new CatalogQuery(null, null, null, null, null, false, false, null, Map.of("ram", Set.of("16 GB")),
                CatalogQuery.Sort.RELEVANCE, 1, 24);
        assertThat(ids(CatalogSearch.search(catalog, anyRam, Map.of()))).containsExactlyInAnyOrder(1L, 2L);

        CatalogQuery cheapAvailableOffers = new CatalogQuery(null, null, null, new BigDecimal("1000"), new BigDecimal("4000"), true, true,
                null, null, CatalogQuery.Sort.PRICE_ASC, 1, 24);
        assertThat(ids(CatalogSearch.search(catalog, cheapAvailableOffers, Map.of()))).containsExactly(2L, 1L); // promo prices 3654.15, 3909.15
    }

    @Test
    void sortsPaginatesAndSuggestsRelatedProducts() {
        List<PricedProduct> catalog = priced(List.of(LOQ, TUF, IDEAPAD, MONITOR), List.of());
        assertThat(ids(CatalogSearch.search(catalog, sorted(CatalogQuery.Sort.NEWEST), Map.of()))).containsExactly(2L, 4L, 3L, 1L);
        assertThat(ids(CatalogSearch.search(catalog, sorted(CatalogQuery.Sort.PRICE_DESC), Map.of()))).containsExactly(1L, 2L, 4L, 3L);
        assertThat(ids(CatalogSearch.search(catalog, sorted(CatalogQuery.Sort.POPULAR), Map.of(3L, 9L, 4L, 2L)))).startsWith(3L, 4L);
        CatalogPage second = CatalogSearch.search(catalog, new CatalogQuery(null, null, null, null, null, false, false, null, null,
                CatalogQuery.Sort.NAME, 2, 3), Map.of());
        assertThat(second.pages()).isEqualTo(2);
        assertThat(second.items()).hasSize(1);
        // Related: same main category, in stock, never the product itself; same subcategory first.
        assertThat(CatalogSearch.related(catalog, LOQ, 4)).extracting(p -> p.product().id()).containsExactly(2L);
        assertThatThrownBy(() -> CatalogQuery.Sort.parse("random")).hasMessageContaining("sort");
    }

    @Test
    void checkoutAddsShippingByMethodAndValidatesThePaymentMethod() {
        Quote quote = Quote.price(List.of(new CartItem(1, 1)), Map.of(1L, LOQ), p -> PriceTag.of(p, List.of(GAMING_WEEK), NOW));
        assertThat(quote.lines().get(0).listUnitPrice()).isEqualByComparingTo("4599.00");
        assertThat(quote.lines().get(0).promotionId()).isEqualTo(1L);
        assertThat(quote.savings()).isEqualByComparingTo("689.85");
        CheckoutSummary standard = CheckoutSummary.of(quote, DeliveryMethod.STANDARD, PaymentMethod.CASH_ON_DELIVERY);
        assertThat(standard.shippingCost()).isEqualByComparingTo("0.00"); // free from S/ 299
        CheckoutSummary express = CheckoutSummary.of(quote, DeliveryMethod.EXPRESS, PaymentMethod.BANK_TRANSFER);
        assertThat(express.total()).isEqualByComparingTo("3939.05");
        assertThat(DeliveryMethod.STANDARD.costFor(new BigDecimal("120.00"))).isEqualByComparingTo("15.00");
        assertThatThrownBy(() -> CheckoutSummary.of(quote, DeliveryMethod.PICKUP, PaymentMethod.CASH_ON_DELIVERY))
                .hasMessageContaining("no está disponible");
        assertThatThrownBy(() -> CheckoutSummary.of(quote, DeliveryMethod.EXPRESS, PaymentMethod.PAY_IN_STORE))
                .hasMessageContaining("no está disponible");
        assertThat(CheckoutSummary.of(quote, DeliveryMethod.PICKUP, PaymentMethod.PAY_IN_STORE).shippingCost()).isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> DeliveryMethod.parse("DRONE")).hasMessageContaining("deliveryMethod");
    }

    private static CatalogQuery query(String text, Long category) {
        return new CatalogQuery(text, category, null, null, null, false, false, null, null, CatalogQuery.Sort.RELEVANCE, 1, 24);
    }

    private static CatalogQuery sorted(CatalogQuery.Sort sort) {
        return new CatalogQuery(null, null, null, null, null, false, false, null, null, sort, 1, 24);
    }

    private static List<Long> ids(CatalogPage page) {
        return page.items().stream().map(p -> p.product().id()).collect(Collectors.toList());
    }
}
