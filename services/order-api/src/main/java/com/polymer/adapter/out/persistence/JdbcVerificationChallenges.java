package com.polymer.adapter.out.persistence;

import com.polymer.application.port.out.VerificationChallenges;
import com.polymer.domain.identity.VerificationChallenge;
import com.polymer.domain.identity.VerificationPurpose;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcVerificationChallenges implements VerificationChallenges {
    private final JdbcTemplate jdbc;

    public JdbcVerificationChallenges(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void save(VerificationChallenge c) {
        Jdbc.run(() -> jdbc.update("INSERT INTO verification_challenges (id, user_id, purpose, code_hash, expires_at, attempts_left, sends, last_sent_at, consumed) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO UPDATE SET code_hash = EXCLUDED.code_hash, expires_at = EXCLUDED.expires_at, "
                + "attempts_left = EXCLUDED.attempts_left, sends = EXCLUDED.sends, last_sent_at = EXCLUDED.last_sent_at, consumed = EXCLUDED.consumed",
                c.id(), c.userId(), c.purpose().name(), c.codeHash(), Timestamp.from(c.expiresAt()), c.attemptsLeft(), c.sends(),
                Timestamp.from(c.lastSentAt()), c.consumed()));
    }

    @Override
    public Optional<VerificationChallenge> find(UUID id) {
        return Jdbc.run(() -> jdbc.query("SELECT id, user_id, purpose, code_hash, expires_at, attempts_left, sends, last_sent_at, consumed "
                + "FROM verification_challenges WHERE id = ?", (rs, n) -> new VerificationChallenge(rs.getObject("id", UUID.class), rs.getLong("user_id"),
                VerificationPurpose.valueOf(rs.getString("purpose")), rs.getString("code_hash"), Jdbc.instant(rs.getTimestamp("expires_at")),
                rs.getInt("attempts_left"), rs.getInt("sends"), Jdbc.instant(rs.getTimestamp("last_sent_at")), rs.getBoolean("consumed")), id))
                .stream().findFirst();
    }
}
