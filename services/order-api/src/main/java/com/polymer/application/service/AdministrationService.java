package com.polymer.application.service;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AdminDashboard;
import com.polymer.application.port.in.AdministrationUseCases;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.SystemHealth;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.CatalogRepository;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.IncidentRepository;
import com.polymer.application.port.out.SystemHealthPort;
import com.polymer.application.port.out.UserRepository;
import com.polymer.domain.audit.AuditEvent;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdministrationService implements AdministrationUseCases {
    private static final int LOW_STOCK_THRESHOLD = 5;

    private final CommerceOrderRepository orders;
    private final CatalogRepository catalog;
    private final IncidentRepository incidents;
    private final UserRepository users;
    private final AuditLog audit;
    private final SystemHealthPort health;
    private final Clock clock;

    public AdministrationService(CommerceOrderRepository orders, CatalogRepository catalog, IncidentRepository incidents,
                                 UserRepository users, AuditLog audit, SystemHealthPort health, Clock clock) {
        this.orders = orders;
        this.catalog = catalog;
        this.incidents = incidents;
        this.users = users;
        this.audit = audit;
        this.health = health;
        this.clock = clock;
    }

    @Override
    public AdminDashboard dashboard(AuthenticatedUser admin) {
        Guards.require(admin, Role.ADMIN);
        return new AdminDashboard(orders.countAll(), orders.countByStatus(), orders.shopSales(), orders.countShopOrders(),
                catalog.countProducts(), catalog.lowStock(LOW_STOCK_THRESHOLD), incidents.countOpen(),
                orders.ordersPerDay(clock.instant().minus(Duration.ofDays(13))), orders.salesByCategory(), audit.recent(12),
                orders.legacyUsdSales(), orders.topProducts(10));
    }

    @Override
    public List<UserAccount> users(AuthenticatedUser admin) {
        Guards.require(admin, Role.ADMIN);
        return users.list();
    }

    @Override
    public UserAccount updateRoles(AuthenticatedUser admin, long userId, Set<Role> roles) {
        Guards.require(admin, Role.ADMIN);
        if (roles == null || roles.isEmpty()) throw new IllegalArgumentException("roles: al menos un rol");
        if (userId == admin.userId() && !roles.contains(Role.ADMIN)) {
            throw new BusinessConflictException("SELF_LOCKOUT", "No puedes quitarte tu propio rol ADMIN");
        }
        UserAccount updated = users.updateRoles(userId, roles).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        audit.record(admin.username(), "ROLES_CHANGED", "user:" + updated.username(), roles.toString());
        return updated;
    }

    @Override
    public UserAccount setEnabled(AuthenticatedUser admin, long userId, boolean enabled) {
        Guards.require(admin, Role.ADMIN);
        if (userId == admin.userId() && !enabled) {
            throw new BusinessConflictException("SELF_LOCKOUT", "No puedes desactivar tu propia cuenta");
        }
        UserAccount updated = users.setEnabled(userId, enabled).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        audit.record(admin.username(), enabled ? "USER_ENABLED" : "USER_DISABLED", "user:" + updated.username(), "");
        return updated;
    }

    @Override
    public List<AuditEvent> auditTrail(AuthenticatedUser admin, int limit) {
        Guards.require(admin, Role.ADMIN);
        return audit.recent(Math.max(1, Math.min(limit, 500)));
    }

    @Override
    public SystemHealth health(AuthenticatedUser viewer) {
        Guards.require(viewer, Role.OBSERVABILITY_VIEWER, Role.ADMIN);
        boolean database = health.databaseUp();
        Map<String, Long> byStatus = database ? orders.countByStatus() : Map.of();
        long open = database ? incidents.countOpen() : 0;
        return new SystemHealth(database, health.riskEngineUp(), byStatus, open, clock.instant());
    }
}
