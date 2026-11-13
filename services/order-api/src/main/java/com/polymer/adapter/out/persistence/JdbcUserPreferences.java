package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.UserPreferences;
import com.polymer.domain.identity.Language;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcUserPreferences implements UserPreferences {
    private final JdbcTemplate jdbc;

    public JdbcUserPreferences(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Language language(long userId) {
        return Jdbc.run(() -> jdbc.query("SELECT language FROM user_preferences WHERE user_id = ?",
                (rs, n) -> Language.orDefault(rs.getString(1)), userId).stream().findFirst().orElse(Language.DEFAULT));
    }

    @Override
    public void setLanguage(long userId, Language language) {
        Jdbc.run(() -> jdbc.update("INSERT INTO user_preferences (user_id, language) VALUES (?, ?) "
                + "ON CONFLICT (user_id) DO UPDATE SET language = EXCLUDED.language, updated_at = now()", userId, language.code()));
    }
}
