package com.polymer.application.service;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AccountUseCases;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.PasswordBlocklist;
import com.polymer.application.port.out.PasswordHasher;
import com.polymer.application.port.out.StoredCredentials;
import com.polymer.application.port.out.UserPreferences;
import com.polymer.application.port.out.UserRepository;
import com.polymer.domain.identity.Language;
import com.polymer.domain.identity.PasswordPolicy;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class AccountService implements AccountUseCases {
    /** Demo accounts, one per role (plus a second logistics operator to demonstrate assignment). */
    static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser("admin", "Administración Polymer", "admin@polymer.local", EnumSet.of(Role.ADMIN)),
            new DemoUser("operador", "Operador de pedidos", "operador@polymer.local", EnumSet.of(Role.ORDER_OPERATOR)),
            new DemoUser("logistica", "Ana Logística", "logistica@polymer.local", EnumSet.of(Role.LOGISTICS_OPERATOR)),
            new DemoUser("logistica2", "Bruno Logística", "logistica2@polymer.local", EnumSet.of(Role.LOGISTICS_OPERATOR)),
            new DemoUser("observador", "Observabilidad", "observador@polymer.local", EnumSet.of(Role.OBSERVABILITY_VIEWER)),
            new DemoUser("cliente", "Cliente de demostración", "cliente@polymer.local", EnumSet.of(Role.CUSTOMER)));

    record DemoUser(String username, String displayName, String email, Set<Role> roles) {}

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AuditLog audit;

    private final PasswordBlocklist blocklist;
    private final UserPreferences preferences;

    public AccountService(UserRepository users, PasswordHasher hasher, AuditLog audit, PasswordBlocklist blocklist, UserPreferences preferences) {
        this.users = users;
        this.hasher = hasher;
        this.audit = audit;
        this.blocklist = blocklist;
        this.preferences = preferences;
    }

    @Override
    public void setLanguage(AuthenticatedUser user, String language) {
        me(user);
        preferences.setLanguage(user.userId(), Language.fromCode(language));
    }

    @Override
    public UserAccount register(String username, String displayName, String email, String password) {
        UserAccount.validateUsername(username);
        UserAccount.validateDisplayName(displayName == null ? null : displayName.strip());
        UserAccount.validateEmail(email == null ? null : email.strip());
        PasswordPolicy.require(password, List.of(username, displayName, email), blocklist::contains);
        if (users.usernameExists(username)) throw new BusinessConflictException("DUPLICATE_USERNAME", "Ese nombre de usuario ya existe");
        if (users.emailExists(email.strip().toLowerCase(), null)) throw new BusinessConflictException("DUPLICATE_EMAIL", "Ese correo ya está registrado");
        UserAccount created = users.create(username, displayName.strip(), email.strip().toLowerCase(), hasher.hash(password), EnumSet.of(Role.CUSTOMER));
        audit.record(username, "USER_REGISTERED", "user:" + username, "CUSTOMER");
        return created;
    }

    @Override
    public UserAccount me(AuthenticatedUser user) {
        if (user == null) throw new ForbiddenOperationException("Sesión requerida");
        return users.findById(user.userId()).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    @Override
    public UserAccount updateProfile(AuthenticatedUser user, String displayName, String email) {
        me(user);
        UserAccount.validateDisplayName(displayName == null ? null : displayName.strip());
        UserAccount.validateEmail(email == null ? null : email.strip());
        String normalizedEmail = email.strip().toLowerCase();
        if (users.emailExists(normalizedEmail, user.userId())) throw new BusinessConflictException("DUPLICATE_EMAIL", "Ese correo ya está registrado");
        UserAccount updated = users.updateProfile(user.userId(), displayName.strip(), normalizedEmail).orElseThrow();
        audit.record(user.username(), "PROFILE_UPDATED", "user:" + user.username(), "");
        return updated;
    }

    @Override
    public void changePassword(AuthenticatedUser user, String currentPassword, String newPassword) {
        StoredCredentials credentials = users.credentials(user.userId()).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        if (currentPassword == null || !hasher.matches(currentPassword, credentials.passwordHash())) {
            throw new IllegalArgumentException("currentPassword: la contraseña actual no es correcta");
        }
        UserAccount account = me(user);
        PasswordPolicy.require(newPassword, List.of(account.username(), account.displayName(), account.email()), blocklist::contains);
        users.updatePasswordHash(user.userId(), hasher.hash(newPassword));
        audit.record(user.username(), "PASSWORD_CHANGED", "user:" + user.username(), "");
    }

    @Override
    public int ensureDemoUsers(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new IllegalArgumentException("La contraseña de demostración debe tener entre 8 y 72 caracteres");
        }
        int created = 0;
        String hash = null;
        for (DemoUser demo : DEMO_USERS) {
            if (users.usernameExists(demo.username())) continue; // never touch existing accounts
            if (hash == null) hash = hasher.hash(password);
            users.create(demo.username(), demo.displayName(), demo.email(), hash, demo.roles());
            audit.record("system", "DEMO_USER_CREATED", "user:" + demo.username(), demo.roles().toString());
            created++;
        }
        return created;
    }

    @Override
    public void recordAuthentication(String username, boolean success) {
        String safe = username == null ? "?" : username.length() > 64 ? username.substring(0, 64) : username;
        audit.record(safe, success ? "LOGIN_SUCCEEDED" : "LOGIN_FAILED", "user:" + safe, "");
    }
}
