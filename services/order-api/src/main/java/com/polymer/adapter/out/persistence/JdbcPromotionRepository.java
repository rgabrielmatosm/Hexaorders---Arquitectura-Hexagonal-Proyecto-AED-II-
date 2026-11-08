package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.PromotionRepository;
import com.polymer.domain.catalog.Promotion;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** promotions + promotion_targets (one row per category or product). */
public final class JdbcPromotionRepository implements PromotionRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcPromotionRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private record Row(long id, String slug, String name, String description, int percent, java.time.Instant starts,
                       java.time.Instant ends, boolean active) {}

    @Override
    public List<Promotion> all() { return Jdbc.run(() -> load("", new Object[0])); }

    @Override
    public Optional<Promotion> find(long id) { return Jdbc.run(() -> load("WHERE id = ?", new Object[] {id}).stream().findFirst()); }

    private List<Promotion> load(String where, Object[] args) {
        List<Row> rows = jdbc.query("SELECT id, slug, name, description, discount_percent, starts_at, ends_at, active FROM promotions "
                + where + " ORDER BY id", (rs, n) -> new Row(rs.getLong("id"), rs.getString("slug"), rs.getString("name"),
                rs.getString("description"), rs.getInt("discount_percent"), rs.getTimestamp("starts_at").toInstant(),
                rs.getTimestamp("ends_at").toInstant(), rs.getBoolean("active")), args);
        Map<Long, Set<Long>> categories = new HashMap<>();
        Map<Long, Set<Long>> products = new HashMap<>();
        jdbc.query("SELECT promotion_id, category_id, product_id FROM promotion_targets", rs -> {
            long promotion = rs.getLong("promotion_id");
            Long category = rs.getObject("category_id", Long.class);
            Long product = rs.getObject("product_id", Long.class);
            if (category != null) categories.computeIfAbsent(promotion, k -> new HashSet<>()).add(category);
            if (product != null) products.computeIfAbsent(promotion, k -> new HashSet<>()).add(product);
        });
        List<Promotion> result = new ArrayList<>();
        for (Row r : rows) {
            result.add(new Promotion(r.id(), r.slug(), r.name(), r.description(), r.percent(), r.starts(), r.ends(), r.active(),
                    categories.getOrDefault(r.id(), Set.of()), products.getOrDefault(r.id(), Set.of())));
        }
        return result;
    }

    @Override
    public boolean slugExists(String slug, Long excludingId) {
        return Jdbc.run(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM promotions WHERE slug = ? AND (?::bigint IS NULL OR id <> ?::bigint))",
                Boolean.class, slug, excludingId, excludingId)));
    }

    @Override
    public Promotion create(Promotion p) {
        return Jdbc.run(() -> tx.execute(status -> {
            Long id = jdbc.queryForObject("INSERT INTO promotions (slug, name, description, discount_percent, starts_at, ends_at, active) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id", Long.class, p.slug(), p.name(), p.description(), p.discountPercent(),
                    Jdbc.ts(p.startsAt()), Jdbc.ts(p.endsAt()), p.active());
            writeTargets(id, p);
            return load("WHERE id = ?", new Object[] {id}).get(0);
        }));
    }

    @Override
    public Optional<Promotion> update(long id, Promotion p) {
        return Jdbc.run(() -> tx.execute(status -> {
            int rows = jdbc.update("UPDATE promotions SET slug = ?, name = ?, description = ?, discount_percent = ?, starts_at = ?, "
                    + "ends_at = ?, active = ?, updated_at = now() WHERE id = ?", p.slug(), p.name(), p.description(), p.discountPercent(),
                    Jdbc.ts(p.startsAt()), Jdbc.ts(p.endsAt()), p.active(), id);
            if (rows == 0) return Optional.<Promotion>empty();
            jdbc.update("DELETE FROM promotion_targets WHERE promotion_id = ?", id);
            writeTargets(id, p);
            return load("WHERE id = ?", new Object[] {id}).stream().findFirst();
        }));
    }

    private void writeTargets(long id, Promotion p) {
        for (Long category : p.categoryIds()) jdbc.update("INSERT INTO promotion_targets (promotion_id, category_id) VALUES (?, ?)", id, category);
        for (Long product : p.productIds()) jdbc.update("INSERT INTO promotion_targets (promotion_id, product_id) VALUES (?, ?)", id, product);
    }
}
