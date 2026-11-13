package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.TestimonialRepository;
import com.polymer.domain.testimonial.Testimonial;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** v9 · Adaptador de salida JDBC de los testimonios (PostgreSQL, tabla testimonials). */
public final class JdbcTestimonialRepository implements TestimonialRepository {
    private static final String COLUMNS = "id, kind, user_id, name, role, photo_url, linkedin_url, comment, rating, visible, position, created_at";
    private static final RowMapper<Testimonial> ROW = (rs, n) -> new Testimonial(rs.getLong("id"),
            Testimonial.Kind.valueOf(rs.getString("kind")), (Long) rs.getObject("user_id", Long.class), rs.getString("name"),
            rs.getString("role"), rs.getString("photo_url"), rs.getString("linkedin_url"), rs.getString("comment"),
            (Integer) rs.getObject("rating", Integer.class), rs.getBoolean("visible"), rs.getInt("position"),
            Jdbc.instant(rs.getTimestamp("created_at")));
    private final JdbcTemplate jdbc;

    public JdbcTestimonialRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Testimonial> visible() {
        return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM testimonials WHERE visible "
                + "ORDER BY CASE kind WHEN 'TEAM' THEN 0 ELSE 1 END, position, created_at DESC, id DESC LIMIT 60", ROW));
    }

    @Override
    public List<Testimonial> all() {
        return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM testimonials "
                + "ORDER BY CASE kind WHEN 'TEAM' THEN 0 ELSE 1 END, position, created_at DESC, id DESC LIMIT 500", ROW));
    }

    @Override
    public Optional<Testimonial> find(long id) {
        return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM testimonials WHERE id = ?", ROW, id)).stream().findFirst();
    }

    @Override
    public Optional<Testimonial> forUser(long userId) {
        return Jdbc.run(() -> jdbc.query("SELECT " + COLUMNS + " FROM testimonials WHERE user_id = ?", ROW, userId)).stream().findFirst();
    }

    @Override
    public Testimonial save(Testimonial t) {
        // Uno por cuenta: si ya existe, se reemplaza el texto (y vuelve a ser visible solo si no estaba oculto por la administración).
        Long id = Jdbc.run(() -> jdbc.queryForObject("INSERT INTO testimonials (kind, user_id, name, role, photo_url, linkedin_url, comment, "
                + "rating, visible, position, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (user_id) DO UPDATE SET name = EXCLUDED.name, role = EXCLUDED.role, linkedin_url = EXCLUDED.linkedin_url, "
                + "comment = EXCLUDED.comment, rating = EXCLUDED.rating, updated_at = EXCLUDED.updated_at RETURNING id", Long.class,
                t.kind().name(), t.userId(), t.name(), t.role(), t.photoUrl(), t.linkedinUrl(), t.comment(), t.rating(), t.visible(),
                t.position(), Jdbc.ts(t.createdAt()), Jdbc.ts(t.createdAt())));
        return find(id).orElseThrow();
    }

    @Override
    public void setVisible(long id, boolean visible) {
        Jdbc.run(() -> jdbc.update("UPDATE testimonials SET visible = ?, updated_at = now() WHERE id = ?", visible, id));
    }
}
