package com.hexaorders.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.CommercialStatus;
import com.hexaorders.domain.catalog.Product;
import com.hexaorders.domain.catalog.ProductDetails;
import com.hexaorders.domain.catalog.Spec;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;

/**
 * PostgreSQL catalog. v6: two-level categories (parent_id), the technical sheet in JSONB (specs:
 * [{label, value}], attributes: {key: value}), previous price and commercial status.
 */
public final class JdbcCatalogRepository implements CatalogRepository {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<Spec>> SPECS = new TypeReference<>() { };
    private static final TypeReference<LinkedHashMap<String, String>> ATTRIBUTES = new TypeReference<>() { };
    private static final String SELECT = "SELECT p.id, p.sku, p.name, p.description, p.category_id, c.name AS category_name, "
            + "p.price, p.stock, p.active, p.hue, p.brand, p.model, p.long_description, p.specs::text AS specs, "
            + "p.attributes::text AS attributes, p.compare_at_price, p.commercial_status, "
            + "COALESCE(c.parent_id, c.id) AS main_id, COALESCE(m.name, c.name) AS main_name, p.created_at, p.updated_at "
            + "FROM products p JOIN categories c ON c.id = p.category_id LEFT JOIN categories m ON m.id = c.parent_id ";
    private static final String CATEGORY_COLUMNS = "id, slug, name, parent_id, position, active";
    private static final RowMapper<Product> PRODUCT = (rs, n) -> new Product(rs.getLong("id"), details(rs),
            rs.getString("category_name"), rs.getInt("stock"), rs.getLong("main_id"), rs.getString("main_name"),
            Jdbc.instant(rs.getTimestamp("created_at")), Jdbc.instant(rs.getTimestamp("updated_at")));
    private static final RowMapper<Category> CATEGORY = (rs, n) -> new Category(rs.getLong("id"), rs.getString("slug"),
            rs.getString("name"), rs.getObject("parent_id", Long.class), rs.getInt("position"), rs.getBoolean("active"));

    private final JdbcTemplate jdbc;

    public JdbcCatalogRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static ProductDetails details(ResultSet rs) throws SQLException {
        try {
            return new ProductDetails(rs.getString("sku"), rs.getString("name"), rs.getString("description"), rs.getLong("category_id"),
                    rs.getBigDecimal("price"), rs.getBoolean("active"), rs.getInt("hue"), rs.getString("brand"), rs.getString("model"),
                    rs.getString("long_description"), JSON.readValue(rs.getString("specs"), SPECS),
                    JSON.readValue(rs.getString("attributes"), ATTRIBUTES), rs.getBigDecimal("compare_at_price"),
                    CommercialStatus.valueOf(rs.getString("commercial_status")));
        } catch (JsonProcessingException corrupt) {
            throw new SQLException("Ficha técnica no válida del producto " + rs.getLong("id"), corrupt);
        }
    }

    private static String json(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    @Override
    public List<Category> categories() {
        return Jdbc.run(() -> jdbc.query("SELECT " + CATEGORY_COLUMNS + " FROM categories ORDER BY parent_id NULLS FIRST, position, name", CATEGORY));
    }

    @Override
    public Optional<Category> category(long id) {
        return Jdbc.run(() -> jdbc.query("SELECT " + CATEGORY_COLUMNS + " FROM categories WHERE id = ?", CATEGORY, id).stream().findFirst());
    }

    @Override
    public Category createCategory(String slug, String name) { return createCategory(slug, name, null); }

    @Override
    public Category createCategory(String slug, String name, Long parentId) {
        return Jdbc.run(() -> jdbc.queryForObject("INSERT INTO categories (slug, name, parent_id, position) VALUES (?, ?, ?, "
                + "(SELECT COALESCE(max(position), 0) + 1 FROM categories WHERE parent_id IS NOT DISTINCT FROM ?)) RETURNING " + CATEGORY_COLUMNS,
                CATEGORY, slug, name, parentId, parentId));
    }

    @Override
    public List<Product> products(String query, Long categoryId, boolean onlyActive) {
        StringBuilder sql = new StringBuilder(SELECT).append("WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (onlyActive) sql.append("AND p.active AND c.active ");
        if (categoryId != null) {
            // A main category includes its subcategories.
            sql.append("AND (p.category_id = ? OR c.parent_id = ?) ");
            args.add(categoryId);
            args.add(categoryId);
        }
        if (query != null) {
            // Parameterized ILIKE; % and _ typed by the user are escaped.
            sql.append("AND (p.name ILIKE ? ESCAPE '\\' OR p.description ILIKE ? ESCAPE '\\' OR p.sku ILIKE ? ESCAPE '\\' "
                    + "OR p.brand ILIKE ? ESCAPE '\\' OR p.model ILIKE ? ESCAPE '\\') ");
            String pattern = "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            for (int i = 0; i < 5; i++) args.add(pattern);
        }
        sql.append("ORDER BY main_name, c.name, p.name");
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
                    "INSERT INTO products (sku, name, description, category_id, price, stock, active, hue, brand, model, long_description, "
                            + "specs, attributes, compare_at_price, commercial_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?)",
                    new String[] {"id"});
            statement.setString(1, d.sku());
            statement.setString(2, d.name());
            statement.setString(3, d.description());
            statement.setLong(4, d.categoryId());
            statement.setBigDecimal(5, d.price());
            statement.setInt(6, initialStock);
            statement.setBoolean(7, d.active());
            statement.setInt(8, d.hue());
            statement.setString(9, d.brand());
            statement.setString(10, d.model());
            statement.setString(11, d.longDescription());
            statement.setString(12, json(d.specs()));
            statement.setString(13, json(d.attributes()));
            statement.setBigDecimal(14, d.compareAtPrice());
            statement.setString(15, d.status().name());
            return statement;
        }, keys));
        return product(keys.getKey().longValue()).orElseThrow();
    }

    @Override
    public Optional<Product> updateProduct(long id, ProductDetails d) {
        int rows = Jdbc.run(() -> jdbc.update("UPDATE products SET sku = ?, name = ?, description = ?, category_id = ?, price = ?, "
                + "active = ?, hue = ?, brand = ?, model = ?, long_description = ?, specs = ?::jsonb, attributes = ?::jsonb, "
                + "compare_at_price = ?, commercial_status = ?, updated_at = now() WHERE id = ?", d.sku(), d.name(), d.description(),
                d.categoryId(), d.price(), d.active(), d.hue(), d.brand(), d.model(), d.longDescription(), json(d.specs()),
                json(d.attributes()), d.compareAtPrice(), d.status().name(), id));
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
