package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.AdminDashboard;
import com.polymer.application.port.in.AdministrationUseCases;
import com.polymer.application.port.in.CatalogUseCases;
import com.polymer.domain.audit.AuditEvent;
import com.polymer.domain.catalog.Category;
import com.polymer.domain.catalog.ProductDetails;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import java.math.BigDecimal;
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only (enforced by SecurityConfiguration and again inside each use case). */
@RestController
@RequestMapping("/api/admin")
public final class AdminController {
    private final AdministrationUseCases administration;
    private final CatalogUseCases catalog;
    private final com.polymer.application.port.in.ContactUseCases contact;
    private final com.polymer.application.port.in.PromotionUseCases promotions;

    public AdminController(AdministrationUseCases administration, CatalogUseCases catalog,
                           com.polymer.application.port.in.ContactUseCases contact,
                           com.polymer.application.port.in.PromotionUseCases promotions) {
        this.administration = administration;
        this.catalog = catalog;
        this.contact = contact;
        this.promotions = promotions;
    }

    /** Messages received from the public contact page. */
    @GetMapping("/contact-messages")
    public List<com.polymer.domain.contact.ContactMessage> contactMessages(@RequestParam(defaultValue = "50") int limit) {
        return contact.recent(CurrentUser.require(), limit);
    }

    @GetMapping("/dashboard")
    public AdminDashboard dashboard() { return administration.dashboard(CurrentUser.require()); }

    @GetMapping("/users")
    public List<UserAccount> users() { return administration.users(CurrentUser.require()); }

    @PutMapping("/users/{id}/roles")
    public UserAccount roles(@PathVariable long id, @RequestBody RolesRequest request) {
        Set<Role> roles = EnumSet.noneOf(Role.class);
        if (request.roles() != null) request.roles().forEach(name -> roles.add(Role.valueOf(name)));
        return administration.updateRoles(CurrentUser.require(), id, roles);
    }

    @PutMapping("/users/{id}/enabled")
    public UserAccount enabled(@PathVariable long id, @RequestBody EnabledRequest request) {
        return administration.setEnabled(CurrentUser.require(), id, Boolean.TRUE.equals(request.enabled()));
    }

    @GetMapping("/audit")
    public List<AuditEvent> audit(@RequestParam(defaultValue = "100") int limit) {
        return administration.auditTrail(CurrentUser.require(), limit);
    }

    @GetMapping("/products")
    public List<ProductView> products() {
        return catalog.allProducts(CurrentUser.require()).stream().map(ProductView::of).toList();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductView> create(@RequestBody ProductRequest request) {
        ProductView created = ProductView.of(catalog.createProduct(CurrentUser.require(), request.details(),
                request.stock() == null ? 0 : request.stock()));
        return ResponseEntity.created(URI.create("/api/catalog/products/" + created.id())).body(created);
    }

    /** Fields left out of the request keep their current value (v5 clients only send the basic fields). */
    @PutMapping("/products/{id}")
    public ProductView update(@PathVariable long id, @RequestBody ProductRequest request) {
        var admin = CurrentUser.require();
        ProductDetails current = catalog.allProducts(admin).stream().filter(p -> p.id() == id).findFirst()
                .map(com.polymer.domain.catalog.Product::details)
                .orElseThrow(() -> new com.polymer.application.exception.ResourceNotFoundException("Producto no encontrado"));
        return ProductView.of(catalog.updateProduct(admin, id, request.details(current)));
    }

    @PostMapping("/products/{id}/stock")
    public ProductView stock(@PathVariable long id, @RequestBody StockRequest request) {
        return ProductView.of(catalog.adjustStock(CurrentUser.require(), id, request.delta() == null ? 0 : request.delta(), request.reason()));
    }

    @PostMapping("/categories")
    public ResponseEntity<Category> category(@RequestBody CategoryRequest request) {
        Category created = catalog.createCategory(CurrentUser.require(), request.slug(), request.name(), request.parentId());
        return ResponseEntity.created(URI.create("/api/catalog/categories")).body(created);
    }

    /** Every category, including the ones hidden from the store. */
    @GetMapping("/categories")
    public List<Category> categories() { return catalog.allCategories(CurrentUser.require()); }

    // ---- v6: promotions -----------------------------------------------------------------------
    @GetMapping("/promotions")
    public List<PromotionView> promotions() { return promotions.all(CurrentUser.require()).stream().map(PromotionView::of).toList(); }

    @PostMapping("/promotions")
    public ResponseEntity<PromotionView> createPromotion(@RequestBody PromotionRequest request) {
        PromotionView created = PromotionView.of(promotions.create(CurrentUser.require(), request.toPromotion(0)));
        return ResponseEntity.created(URI.create("/api/admin/promotions/" + created.id())).body(created);
    }

    @PutMapping("/promotions/{id}")
    public PromotionView updatePromotion(@PathVariable long id, @RequestBody PromotionRequest request) {
        return PromotionView.of(promotions.update(CurrentUser.require(), id, request.toPromotion(id)));
    }

    @PutMapping("/promotions/{id}/active")
    public PromotionView activatePromotion(@PathVariable long id, @RequestBody EnabledRequest request) {
        return PromotionView.of(promotions.setActive(CurrentUser.require(), id, Boolean.TRUE.equals(request.enabled())));
    }

    public record RolesRequest(List<String> roles) {}
    public record EnabledRequest(Boolean enabled) {}
    public record StockRequest(Integer delta, String reason) {}
    public record CategoryRequest(String slug, String name, Long parentId) {}

    public record ProductRequest(String sku, String name, String description, Long categoryId, BigDecimal price,
                                 Boolean active, Integer hue, Integer stock,
                                 String brand, String model, String longDescription, List<com.polymer.domain.catalog.Spec> specs,
                                 java.util.Map<String, String> attributes, BigDecimal compareAtPrice, String status) {
        ProductDetails details() { return details(null); }

        /** current: the product being edited (null when creating); missing v6 fields keep its values. */
        ProductDetails details(ProductDetails current) {
            com.polymer.domain.catalog.CommercialStatus parsed = status == null || status.isBlank()
                    ? (current == null ? null : current.status())
                    : com.polymer.domain.catalog.CommercialStatus.valueOf(status.strip().toUpperCase(java.util.Locale.ROOT));
            boolean keepCompare = compareAtPrice == null && current != null && current.compareAtPrice() != null
                    && price != null && current.compareAtPrice().compareTo(price) > 0;
            return new ProductDetails(sku, name, description == null ? "" : description, categoryId == null ? 0 : categoryId,
                    price, active == null || active, hue == null ? (current == null ? 260 : current.hue()) : hue,
                    brand != null ? brand : current == null ? null : current.brand(),
                    model != null ? model : current == null ? null : current.model(),
                    longDescription != null ? longDescription : current == null ? null : current.longDescription(),
                    specs != null ? specs : current == null ? null : current.specs(),
                    attributes != null ? attributes : current == null ? null : current.attributes(),
                    compareAtPrice != null && compareAtPrice.signum() > 0 ? compareAtPrice : keepCompare ? current.compareAtPrice() : null,
                    parsed);
        }
    }

    public record PromotionRequest(String slug, String name, String description, Integer discountPercent, java.time.Instant startsAt,
                                   java.time.Instant endsAt, Boolean active, java.util.Set<Long> categoryIds, java.util.Set<Long> productIds) {
        com.polymer.domain.catalog.Promotion toPromotion(long id) {
            return new com.polymer.domain.catalog.Promotion(id, slug, name, description, discountPercent == null ? 0 : discountPercent,
                    startsAt, endsAt, active == null || active, categoryIds, productIds);
        }
    }
}
