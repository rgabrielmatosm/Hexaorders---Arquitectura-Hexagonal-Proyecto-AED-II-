package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.CartRepository;
import com.polymer.domain.fulfillment.CartItem;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** cart_items: one row per customer and product. */
public final class JdbcCartRepository implements CartRepository {
    private final JdbcTemplate jdbc;

    public JdbcCartRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<CartItem> items(long userId) {
        return Jdbc.run(() -> jdbc.query("SELECT product_id, quantity FROM cart_items WHERE user_id = ? ORDER BY added_at, product_id",
                (rs, n) -> new CartItem(rs.getLong("product_id"), rs.getInt("quantity")), userId));
    }

    @Override
    public void set(long userId, long productId, int quantity) {
        Jdbc.run(() -> jdbc.update("INSERT INTO cart_items (user_id, product_id, quantity) VALUES (?, ?, ?) "
                + "ON CONFLICT (user_id, product_id) DO UPDATE SET quantity = EXCLUDED.quantity, updated_at = now()", userId, productId, quantity));
    }

    @Override
    public void remove(long userId, long productId) {
        Jdbc.run(() -> jdbc.update("DELETE FROM cart_items WHERE user_id = ? AND product_id = ?", userId, productId));
    }

    @Override
    public void clear(long userId) {
        Jdbc.run(() -> jdbc.update("DELETE FROM cart_items WHERE user_id = ?", userId));
    }
}
