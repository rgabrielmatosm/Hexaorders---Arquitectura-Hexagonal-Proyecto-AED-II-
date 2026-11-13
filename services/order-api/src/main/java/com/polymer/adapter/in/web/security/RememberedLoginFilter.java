package com.polymer.adapter.in.web.security;

import com.polymer.application.port.in.SignInUseCases;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * «Recordarme durante 30 días»: si no hay sesión pero sí la cookie POLYMER_REMEMBER, el núcleo la
 * valida (hash, vencimiento, cuenta activa), rota la ficha y se abre una sesión nueva. Una ficha
 * reutilizada (posible robo) cierra todas las sesiones recordadas de esa cuenta.
 */
public final class RememberedLoginFilter extends OncePerRequestFilter {
    private final SignInUseCases signIn;
    private final SessionSignIn sessions;
    private final SecurityTelemetry telemetry;

    public RememberedLoginFilter(SignInUseCases signIn, SessionSignIn sessions, SecurityTelemetry telemetry) {
        this.signIn = signIn;
        this.sessions = sessions;
        this.telemetry = telemetry;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/css/") || path.startsWith("/js/") || path.startsWith("/img/") || path.startsWith("/media/")
                || path.startsWith("/actuator/") || path.equals("/api/auth/logout") || path.startsWith("/i18n/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var current = SecurityContextHolder.getContext().getAuthentication();
        String cookie = AuthCookies.read(request, AuthCookies.REMEMBER);
        if (cookie != null && (current == null || !current.isAuthenticated() || !(current.getPrincipal() instanceof PolymerUserPrincipal))) {
            try {
                Optional<SignInUseCases.ResumedLogin> resumed = signIn.resume(cookie);
                if (resumed.isPresent()) {
                    sessions.complete(resumed.get().userId(), false, request, response);
                    AuthCookies.write(request, response, AuthCookies.REMEMBER, resumed.get().rotatedCookie());
                    telemetry.signedIn("remembered");
                } else {
                    AuthCookies.clear(request, response, AuthCookies.REMEMBER);
                }
            } catch (RuntimeException unavailable) {
                // Base de datos caída o cuenta desactivada: se continúa sin sesión (la petición decide si la necesita).
                logger.debug("Sesión recordada no recuperada", unavailable);
            }
        }
        chain.doFilter(request, response);
    }
}
