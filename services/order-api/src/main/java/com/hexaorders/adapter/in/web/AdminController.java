package com.hexaorders.adapter.in.web;

import com.hexaorders.adapter.in.web.security.CurrentUser;
import com.hexaorders.application.port.in.AdminDashboard;
import com.hexaorders.application.port.in.AdministrationUseCases;
import com.hexaorders.application.port.in.CatalogUseCases;
import com.hexaorders.domain.audit.AuditEvent;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.ProductDetails;
import com.hexaorders.domain.identity.Role;
import com.hexaorders.domain.identity.UserAccount;
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

    public AdminController(AdministrationUseCases administration, CatalogUseCases catalog) {
        this.administration = administration;
        this.catalog = catalog;
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

    @PutMapping("/products/{id}")
    public ProductView update(@PathVariable long id, @RequestBody ProductRequest request) {
        return ProductView.of(catalog.updateProduct(CurrentUser.require(), id, request.details()));
    }

    @PostMapping("/products/{id}/stock")
    public ProductView stock(@PathVariable long id, @RequestBody StockRequest request) {
        return ProductView.of(catalog.adjustStock(CurrentUser.require(), id, request.delta() == null ? 0 : request.delta(), request.reason()));
    }

    @PostMapping("/categories")
    public ResponseEntity<Category> category(@RequestBody CategoryRequest request) {
        Category created = catalog.createCategory(CurrentUser.require(), request.slug(), request.name());
        return ResponseEntity.created(URI.create("/api/catalog/categories")).body(created);
    }

    public record RolesRequest(List<String> roles) {}
    public record EnabledRequest(Boolean enabled) {}
    public record StockRequest(Integer delta, String reason) {}
    public record CategoryRequest(String slug, String name) {}

    public record ProductRequest(String sku, String name, String description, Long categoryId, BigDecimal price,
                                 Boolean active, Integer hue, Integer stock) {
        ProductDetails details() {
            return new ProductDetails(sku, name, description == null ? "" : description, categoryId == null ? 0 : categoryId,
                    price, active == null || active, hue == null ? 260 : hue);
        }
    }
}
