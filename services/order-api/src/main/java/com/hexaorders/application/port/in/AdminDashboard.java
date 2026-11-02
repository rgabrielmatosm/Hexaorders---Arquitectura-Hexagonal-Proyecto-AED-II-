package com.hexaorders.application.port.in;

import com.hexaorders.domain.audit.AuditEvent;
import com.hexaorders.domain.catalog.Product;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Every figure comes from PostgreSQL; nothing is estimated. */
public record AdminDashboard(long totalOrders, Map<String, Long> ordersByStatus, BigDecimal shopSales, long shopOrders,
                             long products, List<Product> lowStock, long openIncidents, Map<LocalDate, Long> ordersPerDay,
                             List<SalesByCategory> salesByCategory, List<AuditEvent> recentActivity) {
    public record SalesByCategory(String category, long units, BigDecimal revenue) {}
}
