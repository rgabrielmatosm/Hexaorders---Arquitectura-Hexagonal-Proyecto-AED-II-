package com.polymer.adapter.in.web.security;

import com.polymer.application.port.in.SignInUseCases;
import com.polymer.domain.identity.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;

/** Abre la sesión HTTP de un usuario ya verificado y gestiona el estado «pendiente de código». */
public final class SessionSignIn {
    private final SignInUseCases signIn;
    private final SecurityContextRepository contexts;
    private final SecurityTelemetry telemetry;

    public SessionSignIn(SignInUseCases signIn, SecurityContextRepository contexts, SecurityTelemetry telemetry) {
        this.signIn = signIn;
        this.contexts = contexts;
        this.telemetry = telemetry;
    }

    /** Sesión autenticada; con remember también la sesión persistente y el dispositivo de confianza (30 días). */
    public PolymerUserPrincipal complete(long userId, boolean remember, HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = signIn.account(userId);
        PolymerUserPrincipal principal = new PolymerUserPrincipal(account.id(), account.username(), account.displayName(), null,
                account.roles(), account.enabled());
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            existing.removeAttribute(PendingSignIn.ATTRIBUTE);
            request.changeSessionId(); // protección contra fijación de sesión
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        if (remember) {
            AuthCookies.write(request, response, AuthCookies.REMEMBER, signIn.rememberLogin(userId));
            AuthCookies.write(request, response, AuthCookies.DEVICE, signIn.trustDevice(userId));
        }
        return principal;
    }

    public void pending(HttpServletRequest request, PendingSignIn pending) {
        SecurityContextHolder.clearContext();
        request.getSession(true).setAttribute(PendingSignIn.ATTRIBUTE, pending);
        telemetry.verificationRequested();
    }

    public Optional<PendingSignIn> pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return Optional.empty();
        return session.getAttribute(PendingSignIn.ATTRIBUTE) instanceof PendingSignIn pending ? Optional.of(pending) : Optional.empty();
    }

    public void clearPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.removeAttribute(PendingSignIn.ATTRIBUTE);
    }
}
