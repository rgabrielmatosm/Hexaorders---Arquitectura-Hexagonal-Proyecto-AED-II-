package com.polymer.application.port.in;

import com.polymer.domain.audit.AuditEvent;
import com.polymer.domain.catalog.Product;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Every figure comes from PostgreSQL; nothing is estimated. v6: the store sells in PEN, so shopSales
 * only adds PEN orders; legacyUsdSales keeps the store orders placed in USD before v6.
 */
public record AdminDashboard(long totalOrders, Map<String, Long> ordersByStatus, BigDecimal shopSales, long shopOrders,
                             long products, List<Product> lowStock, long openIncidents, Map<LocalDate, Long> ordersPerDay,
                             List<SalesByCategory> salesByCategory, List<AuditEvent> recentActivity,
                             BigDecimal legacyUsdSales, List<TopProduct> topProducts) {
    public AdminDashboard(long totalOrders, Map<String, Long> ordersByStatus, BigDecimal shopSales, long shopOrders,
                          long products, List<Product> lowStock, long openIncidents, Map<LocalDate, Long> ordersPerDay,
                          List<SalesByCategory> salesByCategory, List<AuditEvent> recentActivity) {
        this(totalOrders, ordersByStatus, shopSales, shopOrders, products, lowStock, openIncidents, ordersPerDay, salesByCategory,
                recentActivity, BigDecimal.ZERO.setScale(2), List.of());
    }

    /** Sales grouped by MAIN category and currency (orders before v6 were in USD). */
    public record SalesByCategory(String category, long units, BigDecimal revenue, String currency) {
        public SalesByCategory(String category, long units, BigDecimal revenue) { this(category, units, revenue, "PEN"); }
    }

    public record TopProduct(long productId, String name, long units, BigDecimal revenue, String currency) {}
}
