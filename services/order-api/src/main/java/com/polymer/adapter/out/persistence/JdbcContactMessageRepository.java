package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.ContactMessageRepository;
import com.polymer.domain.contact.ContactMessage;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

public final class JdbcContactMessageRepository implements ContactMessageRepository {
    private static final RowMapper<ContactMessage> ROW = (rs, n) -> new ContactMessage(rs.getLong("id"), rs.getString("name"),
            rs.getString("email"), rs.getString("topic"), rs.getString("message"), Jdbc.instant(rs.getTimestamp("created_at")));
    private final JdbcTemplate jdbc;

    public JdbcContactMessageRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public ContactMessage save(ContactMessage m) {
        Long id = Jdbc.run(() -> jdbc.queryForObject("INSERT INTO contact_messages (name, email, topic, message, created_at) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id", Long.class, m.name(), m.email(), m.topic(), m.message(), Jdbc.ts(m.createdAt())));
        return new ContactMessage(id, m.name(), m.email(), m.topic(), m.message(), m.createdAt());
    }

    @Override
    public List<ContactMessage> recent(int limit) {
        return Jdbc.run(() -> jdbc.query("SELECT id, name, email, topic, message, created_at FROM contact_messages "
                + "ORDER BY created_at DESC, id DESC LIMIT ?", ROW, limit));
    }
}
