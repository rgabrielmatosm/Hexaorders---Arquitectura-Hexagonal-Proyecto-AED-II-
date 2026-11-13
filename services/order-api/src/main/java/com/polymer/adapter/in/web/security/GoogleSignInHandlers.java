package com.polymer.adapter.in.web.security;

import com.polymer.application.port.in.SignInStep;
import com.polymer.application.port.in.SignInUseCases;
import com.polymer.application.exception.BusinessConflictException;
import com.polymer.domain.identity.ExternalIdentity;
import com.polymer.domain.identity.ExternalSignInIntent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;

/**
 * Adaptador de entrada de «Continuar con Google» (OpenID Connect). Spring Security valida el token de
 * Google (firma, emisor, audiencia, nonce); aquí solo se traduce a ExternalIdentity y el núcleo decide:
 * vincular o crear la cuenta y pedir el código de 6 dígitos por correo. La autenticación OAuth2 de
 * Spring NO se conserva: la sesión solo queda autenticada tras verificar el código.
 */
public final class GoogleSignInHandlers implements AuthenticationSuccessHandler, AuthenticationFailureHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GoogleSignInHandlers.class);
    public static final String NEXT = "polymer.google.next";
    public static final String LOGIN_PAGE = "polymer.google.loginPage";
    public static final String INTENT = "polymer.google.intent";

    private final SignInUseCases signIn;
    private final SessionSignIn sessions;
    private final SecurityContextRepository contexts;
    private final SecurityTelemetry telemetry;

    public GoogleSignInHandlers(SignInUseCases signIn, SessionSignIn sessions, SecurityContextRepository contexts, SecurityTelemetry telemetry) {
        this.signIn = signIn;
        this.sessions = sessions;
        this.contexts = contexts;
        this.telemetry = telemetry;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // Se descarta la autenticación OAuth2: la sesión aún no está autenticada.
        SecurityContextHolder.clearContext();
        contexts.saveContext(SecurityContextHolder.createEmptyContext(), request, response);
        String next = take(request, NEXT);
        String loginPage = take(request, LOGIN_PAGE);
        if (loginPage == null) loginPage = "/login.html";
        ExternalSignInIntent intent = "SIGN_UP".equals(takeRaw(request, INTENT)) ? ExternalSignInIntent.SIGN_UP : ExternalSignInIntent.SIGN_IN;
        if (!(authentication.getPrincipal() instanceof OidcUser user)) {
            response.sendRedirect(loginPage + "?google=error");
            return;
        }
        try {
            ExternalIdentity identity = new ExternalIdentity("google", user.getSubject(), user.getEmail(),
                    Boolean.TRUE.equals(user.getEmailVerified()), user.getFullName());
            SignInStep step = signIn.withExternalIdentity(identity, AuthCookies.read(request, AuthCookies.DEVICE), intent);
            telemetry.login(true);
            if (step.completed()) {
                sessions.complete(step.userId(), false, request, response);
                telemetry.signedIn("google");
                response.sendRedirect(loginPage + "?google=ok" + (next == null ? "" : "&next=" + URLEncoder.encode(next, StandardCharsets.UTF_8)));
                return;
            }
            sessions.pending(request, new PendingSignIn(step.userId(), step.challenge().id(), false, next));
            response.sendRedirect(loginPage + "?verificar=google" + (step.newAccount() ? "&nueva=1" : "")
                    + (next == null ? "" : "&next=" + URLEncoder.encode(next, StandardCharsets.UTF_8)));
        } catch (BusinessConflictException noAccount) {
            // v9: «Iniciar sesión con Google» sin cuenta POLYMER: se rechaza y la página invita a registrarse.
            LOG.info("Inicio de sesión con Google rechazado: {}", noAccount.code());
            telemetry.login(false);
            response.sendRedirect(loginPage + "?google=" + ("NO_ACCOUNT".equals(noAccount.code()) ? "sin-cuenta" : "error"));
        } catch (RuntimeException refused) {
            LOG.warn("Inicio de sesión con Google rechazado: {}", refused.getClass().getSimpleName());
            telemetry.login(false);
            response.sendRedirect(loginPage + "?google=error");
        }
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        LOG.warn("Google no completó el inicio de sesión: {}", exception.getClass().getSimpleName());
        telemetry.login(false);
        String loginPage = take(request, LOGIN_PAGE);
        response.sendRedirect((loginPage == null ? "/login.html" : loginPage) + "?google=error");
    }

    private static String take(HttpServletRequest request, String attribute) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute(attribute);
        session.removeAttribute(attribute);
        return value instanceof String text && safePath(text) ? text : null;
    }

    private static String takeRaw(HttpServletRequest request, String attribute) {
        HttpSession session = request.getSession(false);
        if (session == null) return null;
        Object value = session.getAttribute(attribute);
        session.removeAttribute(attribute);
        return value instanceof String text ? text : null;
    }

    /** Solo rutas internas («/…», nunca «//dominio»): evita redirecciones abiertas. */
    public static boolean safePath(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//") && !path.contains("\\") && path.length() <= 300
                && !path.contains("\r") && !path.contains("\n");
    }
}
