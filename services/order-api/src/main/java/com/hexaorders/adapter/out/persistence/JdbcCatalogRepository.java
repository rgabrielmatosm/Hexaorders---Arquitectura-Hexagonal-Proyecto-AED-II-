package com.hexaorders.adapter.out.persistence;

import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.ProductDetails;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

public final class JdbcCatalogRepository implements CatalogRepository {
    private static final String SELECT = "SELECT p.id, p.sku, p.name, p.description, p.category_id, c.name AS category_name, "
            + "p.price, p.stock, p.active, p.hue FROM products p JOIN categories c ON c.id = p.category_id ";
    private static final RowMapper<Product> PRODUCT = (rs, n) -> new Product(rs.getLong("id"),
            new ProductDetails(rs.getString("sku"), rs.getString("name"), rs.getString("description"), rs.getLong("category_id"),
                    rs.getBigDecimal("price"), rs.getBoolean("active"), rs.getInt("hue")),
            rs.getString("category_name"), rs.getInt("stock"));
    private static final RowMapper<Category> CATEGORY = (rs, n) -> new Category(rs.getLong("id"), rs.getString("slug"), rs.getString("name"));

    private final JdbcTemplate jdbc;

    public JdbcCatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Category> categories() {
        return Jdbc.run(() -> jdbc.query("SELECT id, slug, name FROM categories ORDER BY name", CATEGORY));
    }

    @Override
    public Optional<Category> category(long id) {
        return Jdbc.run(() -> jdbc.query("SELECT id, slug, name FROM categories WHERE id = ?", CATEGORY, id).stream().findFirst());
    }

    @Override
    public Category createCategory(String slug, String name) {
        return Jdbc.run(() -> jdbc.queryForObject("INSERT INTO categories (slug, name) VALUES (?, ?) RETURNING id, slug, name", CATEGORY, slug, name));
    }

    @Override
    public List<Product> products(String query, Long categoryId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (onlyActive) sql.append("AND p.active ");
        if (categoryId != null) {
            sql.append("AND p.category_id = ? ");
            args.add(categoryId);
        }
        if (query != null) {
            // Parameterized ILIKE; % and _ typed by the user are escaped.
            sql.append("AND (p.name ILIKE ? ESCAPE '\\' OR p.description ILIKE ? ESCAPE '\\' OR p.sku ILIKE ? ESCAPE '\\') ");
            String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        sql.append("ORDER BY c.name, p.name");
        return Jdbc.run(() -> jdbc.query(sql.toString(), PRODUCT, args.toArray()));
    }

    @Override
    public Optional<Product> product(long id) {
        return Jdbc.run(() -> jdbc.query(SELECT + "WHERE p.id = ?", PRODUCT, id).stream().findFirst());
    }

    @Override
    public Map<Long, Product> productsByIds(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        String placeholders = String.join(",", ids.stream().map(id -> "?").toList());
        List<Product> found = Jdbc.run(() -> jdbc.query(SELECT + "WHERE p.id IN (" + placeholders + ")", PRODUCT, ids.toArray()));
        Map<Long, Product> byId = new LinkedHashMap<>();
        found.forEach(product -> byId.put(product.id(), product));
        return byId;
    }

    @Override
    public Product createProduct(ProductDetails d, int initialStock) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        Jdbc.run(() -> jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO products (sku, name, description, category_id, price, stock, active, hue) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, d.sku());
            statement.setString(2, d.name());
            statement.setString(3, d.description());
            statement.setLong(4, d.categoryId());
            statement.setBigDecimal(5, d.price());
            statement.setInt(6, initialStock);
            statement.setBoolean(7, d.active());
            statement.setInt(8, d.hue());
            return statement;
        }, keys));
        return product(keys.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<Product> updateProduct(long id, ProductDetails d) {
        int rows = Jdbc.run(() -> jdbc.update("UPDATE products SET sku = ?, name = ?, description = ?, category_id = ?, price = ?, "
                + "active = ?, hue = ?, updated_at = now() WHERE id = ?", d.sku(), d.name(), d.description(), d.categoryId(),
                d.price(), d.active(), d.hue(), id));
        return rows == 0 ? Optional.empty() : product(id);
    }

    @Override
    public Optional<Product> adjustStock(long id, int delta) {
        int rows = Jdbc.run(() -> jdbc.update("UPDATE products SET stock = stock + ?, updated_at = now() WHERE id = ? AND stock + ? >= 0",
                delta, id, delta));
        return rows == 0 ? Optional.empty() : product(id);
    }

    @Override
    public List<Product> lowStock(int threshold) {
        return Jdbc.run(() -> jdbc.query(SELECT + "WHERE p.active AND p.stock <= ? ORDER BY p.stock, p.name", PRODUCT, threshold));
    }

    @Override
    public long countProducts() {
        return Jdbc.run(() -> jdbc.queryForObject("SELECT count(*) FROM products", Long.class));
    }

    @Override
    public boolean skuExists(String sku, Long excludingId) {
        return Jdbc.run(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM products WHERE sku = ? AND (?::bigint IS NULL OR id <> ?::bigint))",
                Boolean.class, sku, excludingId, excludingId)));
    }

}
