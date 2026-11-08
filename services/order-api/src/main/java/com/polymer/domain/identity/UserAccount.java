package com.polymer.domain.identity;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** A platform user without credentials (passwords are an infrastructure concern). */
public record UserAccount(long id, String username, String displayName, String email,
                          Set<Role> roles, boolean enabled, Instant createdAt) {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_-]{3,32}");
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]{1,190}\\.[^@\\s]{2,24}");

    public UserAccount {
        validateUsername(username);
        validateDisplayName(displayName);
        validateEmail(email);
        Objects.requireNonNull(roles, "roles");
        roles = roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public boolean hasRole(Role role) { return roles.contains(role); }

    public static void validateUsername(String username) {
        if (username == null || !USERNAME.matcher(username).matches()) {
            throw new IllegalArgumentException("username: entre 3 y 32 letras, dígitos, guiones o guiones bajos");
        }
    }

    public static void validateDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank() || displayName.length() > 80) {
            throw new IllegalArgumentException("displayName: obligatorio, máximo 80 caracteres");
        }
    }

    public static void validateEmail(String email) {
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("email: dirección no válida");
        }
    }

    // La política de contraseñas está en PasswordPolicy (longitud, contraseñas comunes, datos personales).
}
