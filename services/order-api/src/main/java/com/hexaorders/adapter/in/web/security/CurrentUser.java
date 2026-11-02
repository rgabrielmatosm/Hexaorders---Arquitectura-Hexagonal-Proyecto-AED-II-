package com.hexaorders.adapter.in.web.security;

import com.hexaorders.application.exception.ForbiddenOperationException;
import com.hexaorders.application.port.in.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentUser {
    private CurrentUser() {}

    public static AuthenticatedUser require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof HexaUserPrincipal principal) {
            return principal.toUser();
        }
        throw new ForbiddenOperationException("Sesión requerida");
    }
}
