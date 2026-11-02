package com.hexaorders.adapter.out.persistence;

import com.hexaorders.application.exception.BusinessConflictException;
import com.hexaorders.application.exception.PersistenceUnavailableException;
import com.hexaorders.application.port.out.StoredCredentials;
import com.hexaorders.application.port.out.UserRepository;
import com.hexaorders.domain.identity.Role;
import com.hexaorders.domain.identity.UserAccount;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcUserRepository implements UserRepository {
    private static final String COLUMNS = "id, username, display_name, email, password_hash, enabled, created_at";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public JdbcUserRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.tx = tx;
    }

    private List<StoredCredentials> query(String where, Object... args) {
        return Jdbc.run(() -> {
            List<Object[]> rows = jdbc.query("SELECT " + COLUMNS + " FROM app_users " + where, (rs, n) -> new Object[] {
                rs.getLong("id"), rs.getString("username"), rs.getString("display_name"), rs.getString("email"),
                rs.getString("password_hash"), rs.getBoolean("enabled"), rs.getTimestamp("created_at").toInstant()}, args);
            if (rows.isEmpty()) return List.of();
            Map<Long, Set<Role>> roles = new HashMap<>();
            String placeholders = String.join(",", rows.stream().map(r -> "?").toList());
            jdbc.query("SELECT user_id, role FROM app_user_roles WHERE user_id IN (" + placeholders + ")", rs -> {
                roles.computeIfAbsent(rs.getLong(1), k -> EnumSet.noneOf(Role.class)).add(Role.valueOf(rs.getString(2)));
            }, rows.stream().map(r -> r[0]).toArray());
            return rows.stream().map(r -> new StoredCredentials(new UserAccount((Long) r[0], (String) r[1], (String) r[2], (String) r[3],
                    roles.getOrDefault((Long) r[0], EnumSet.noneOf(Role.class)), (Boolean) r[5], (java.time.Instant) r[6]), (String) r[4])).toList();
        });
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        return query("WHERE id = ?", id).stream().findFirst().map(StoredCredentials::account);
    }

    @Override
    public Optional<StoredCredentials> credentials(String username) {
        return query("WHERE username = ?", username).stream().findFirst();
    }

    @Override
    public Optional<StoredCredentials> credentials(long id) {
        return query("WHERE id = ?", id).stream().findFirst();
    }

    @Override
    public boolean usernameExists(String username) {
        return Jdbc.run(() -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM app_users WHERE lower(username) = lower(?))",
                Boolean.class, username)));
    }

    @Override
    public boolean emailExists(String email, Long excludingId) {
        return Jdbc.run(() -> Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM app_users WHERE lower(email) = lower(?) "
                + "AND (?::bigint IS NULL OR id <> ?::bigint))", Boolean.class, email, excludingId, excludingId)));
    }

    @Override
    public UserAccount create(String username, String displayName, String email, String passwordHash, Set<Role> roles) {
        try {
            Long id = tx.execute(status -> {
                Long created = jdbc.queryForObject("INSERT INTO app_users (username, display_name, email, password_hash) VALUES (?, ?, ?, ?) RETURNING id",
                        Long.class, username, displayName, email, passwordHash);
                roles.forEach(role -> jdbc.update("INSERT INTO app_user_roles (user_id, role) VALUES (?, ?)", created, role.name()));
                return created;
            });
            return findById(id).orElseThrow();
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessConflictException("DUPLICATE_USER", "El usuario o el correo ya existen");
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    @Override
    public List<UserAccount> list() {
        return query("ORDER BY created_at, id").stream().map(StoredCredentials::account).toList();
    }

    @Override
    public List<UserAccount> withRole(Role role) {
        return query("WHERE id IN (SELECT user_id FROM app_user_roles WHERE role = ?) ORDER BY display_name", role.name())
                .stream().map(StoredCredentials::account).toList();
    }

    @Override
    public Optional<UserAccount> updateRoles(long id, Set<Role> roles) {
        if (findById(id).isEmpty()) return Optional.empty();
        try {
            tx.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM app_user_roles WHERE user_id = ?", id);
                roles.forEach(role -> jdbc.update("INSERT INTO app_user_roles (user_id, role) VALUES (?, ?)", id, role.name()));
            });
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
        return findById(id);
    }

    @Override
    public Optional<UserAccount> setEnabled(long id, boolean enabled) {
        int rows = Jdbc.run(() -> jdbc.update("UPDATE app_users SET enabled = ? WHERE id = ?", enabled, id));
        return rows == 0 ? Optional.empty() : findById(id);
    }

    @Override
    public Optional<UserAccount> updateProfile(long id, String displayName, String email) {
        int rows = Jdbc.run(() -> jdbc.update("UPDATE app_users SET display_name = ?, email = ? WHERE id = ?", displayName, email, id));
        return rows == 0 ? Optional.empty() : findById(id);
    }

    @Override
    public void updatePasswordHash(long id, String passwordHash) {
        Jdbc.run(() -> jdbc.update("UPDATE app_users SET password_hash = ? WHERE id = ?", passwordHash, id));
    }
}
