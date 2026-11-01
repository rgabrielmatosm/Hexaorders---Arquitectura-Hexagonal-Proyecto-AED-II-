package com.hexaorders.application.service;

import com.hexaorders.application.exception.ForbiddenOperationException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.domain.identity.Role;

/** Defense in depth: use cases re-check roles even though the web layer already enforces them. */
final class Guards {
    private Guards() {}

    static void require(AuthenticatedUser user, Role... roles) {
        if (user == null || !user.hasAny(roles)) {
            throw new ForbiddenOperationException("Operación no permitida para tu rol");
        }
    }

    static String text(String value, int max, String field) {
        if (value == null) return null;
        String trimmed = value.strip();
        if (trimmed.length() > max) throw new IllegalArgumentException(field + ": máximo " + max + " caracteres");
        return trimmed.isEmpty() ? null : trimmed;
    }
}
