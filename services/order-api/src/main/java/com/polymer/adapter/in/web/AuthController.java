package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.AuthCookies;
import com.polymer.adapter.in.web.security.GoogleSignInHandlers;
import com.polymer.adapter.in.web.security.PendingSignIn;
import com.polymer.adapter.in.web.security.PolymerUserPrincipal;
import com.polymer.adapter.in.web.security.SecurityTelemetry;
import com.polymer.adapter.in.web.security.SessionSignIn;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AccountUseCases;
import com.polymer.application.port.in.ChallengeView;
import com.polymer.application.port.in.SignInStep;
import com.polymer.application.port.in.SignInUseCases;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.identity.VerificationPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session-based authentication for the SPA (HttpOnly cookie + CSRF token header).
 * v7: two steps. The password (or Google) is the first factor; when the core asks for it, a 6-digit
 * code sent by e-mail completes the sign-in (HTTP 202 until then). «Recordarme» keeps the session
 * and trusts the device for 30 days.
 */
@RestController
@RequestMapping("/api/auth")
public final class AuthController {
    private final AuthenticationManager authenticationManager;
    private final AccountUseCases accounts;
    private final SignInUseCases signIn;
    private final SessionSignIn sessions;
    private final SecurityTelemetry telemetry;
    private final boolean googleEnabled;

    public AuthController(AuthenticationManager authenticationManager, AccountUseCases accounts, SignInUseCases signIn,
                          SessionSignIn sessions, SecurityTelemetry telemetry, GoogleSignInAvailability google) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.signIn = signIn;
        this.sessions = sessions;
        this.telemetry = telemetry;
        this.googleEnabled = google.enabled();
    }

    /** The SPA reads the token here and sends it back in the X-CSRF-TOKEN header on every write. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof PolymerUserPrincipal principal) {
            return Map.of("authenticated", true, "user", view(principal));
        }
        return Map.of("authenticated", false);
    }

    /** v7: métodos de acceso disponibles (la interfaz muestra «Continuar con Google» solo si está configurado). */
    @GetMapping("/providers")
    public Map<String, Object> providers() {
        return Map.of("google", googleEnabled, "emailVerification", signIn.verificationEnabled(),
                "rememberDays", VerificationPolicy.REMEMBER_FOR.toDays(), "codeDigits", VerificationPolicy.CODE_DIGITS);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String username = request.username() == null ? "" : request.username().strip();
        PolymerUserPrincipal principal;
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password() == null ? "" : request.password()));
            principal = ((PolymerUserPrincipal) authentication.getPrincipal()).withoutCredentials();
        } catch (AuthenticationException failed) {
            telemetry.login(false);
            accounts.recordAuthentication(username, false);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
            problem.setTitle("Credenciales no válidas");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
        telemetry.login(true);
        accounts.recordAuthentication(principal.getUsername(), true);
        boolean remember = Boolean.TRUE.equals(request.rememberMe());
        SignInStep step = signIn.afterPassword(principal.toUser().userId(), AuthCookies.read(httpRequest, AuthCookies.DEVICE));
        if (step.completed()) {
            PolymerUserPrincipal signedIn = sessions.complete(step.userId(), remember, httpRequest, httpResponse);
            telemetry.signedIn("password");
            return ResponseEntity.ok(Map.of("authenticated", true, "user", view(signedIn)));
        }
        String next = GoogleSignInHandlers.safePath(request.next()) ? request.next() : null;
        sessions.pending(httpRequest, new PendingSignIn(step.userId(), step.challenge().id(), remember, next));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(pendingBody(step.challenge(), next, false));
    }

    /** v7: estado de la verificación pendiente (al recargar la página o al volver de Google). */
    @GetMapping("/verify")
    public Map<String, Object> pending(HttpServletRequest request) {
        PendingSignIn pending = pendingOrFail(request);
        return pendingBody(signIn.challenge(pending.challengeId()), pending.next(), pending.remember());
    }

    /** v7: comprueba el código de 6 dígitos y abre la sesión. */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody VerifyRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        PendingSignIn pending = pendingOrFail(httpRequest);
        SignInStep step;
        try {
            step = signIn.verify(pending.challengeId(), request.code());
        } catch (RuntimeException failed) {
            telemetry.verification(false);
            throw failed;
        }
        telemetry.verification(true);
        boolean remember = request.rememberMe() == null ? pending.remember() : request.rememberMe();
        PolymerUserPrincipal principal = sessions.complete(step.userId(), remember, httpRequest, httpResponse);
        telemetry.signedIn("verified");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", true);
        body.put("user", view(principal));
        if (pending.next() != null) body.put("next", pending.next());
        return body;
    }

    @PostMapping("/verify/resend")
    public Map<String, Object> resend(HttpServletRequest request) {
        PendingSignIn pending = pendingOrFail(request);
        return pendingBody(signIn.resend(pending.challengeId()), pending.next(), pending.remember());
    }

    @PostMapping("/verify/cancel")
    public ResponseEntity<Void> cancel(HttpServletRequest request) {
        sessions.clearPending(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * v7: inicio de «Continuar con Google». Guarda a dónde volver y la página de acceso (con su idioma)
     * y redirige al flujo OAuth2 de Spring Security.
     */
    @GetMapping("/google")
    public void google(@RequestParam(name = "next", required = false) String next,
                       @RequestParam(name = "from", required = false) String from,
                       @RequestParam(name = "intent", required = false) String intent,
                       HttpServletRequest request, HttpServletResponse response) throws IOException {
        String loginPage = GoogleSignInHandlers.safePath(from) && from.length() <= 60 ? from : "/login.html";
        if (!googleEnabled) {
            response.sendRedirect(loginPage + "?google=off");
            return;
        }
        HttpSession session = request.getSession(true);
        if (GoogleSignInHandlers.safePath(next)) session.setAttribute(GoogleSignInHandlers.NEXT, next);
        session.setAttribute(GoogleSignInHandlers.LOGIN_PAGE, loginPage);
        // v9: «Registrarse con Google» crea la cuenta; «Iniciar sesión con Google» solo entra si ya existe.
        session.setAttribute(GoogleSignInHandlers.INTENT, "registro".equals(intent) ? "SIGN_UP" : "SIGN_IN");
        response.sendRedirect("/oauth2/authorization/google");
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String remembered = AuthCookies.read(request, AuthCookies.REMEMBER);
        if (remembered != null) {
            signIn.forget(remembered);
            AuthCookies.clear(request, response, AuthCookies.REMEMBER);
        }
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        UserAccount created = accounts.register(request.username(), request.displayName(), request.email(), request.password());
        return ResponseEntity.created(URI.create("/api/account/me")).body(Map.of(
                "id", created.id(), "username", created.username(), "displayName", created.displayName(),
                "roles", PolymerUserPrincipal.roleNames(created.roles()),
                "verificationRequired", signIn.verificationEnabled()));
    }

    private PendingSignIn pendingOrFail(HttpServletRequest request) {
        return sessions.pending(request).orElseThrow(() -> new ResourceNotFoundException("No hay ninguna verificación pendiente"));
    }

    private static Map<String, Object> pendingBody(ChallengeView challenge, String next, boolean remember) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("purpose", challenge.purpose().name());
        view.put("email", challenge.maskedEmail());
        view.put("expiresAt", challenge.expiresAt().toString());
        view.put("attemptsLeft", challenge.attemptsLeft());
        view.put("resendInSeconds", challenge.resendInSeconds());
        view.put("canResend", challenge.canResend());
        view.put("digits", VerificationPolicy.CODE_DIGITS);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("authenticated", false);
        body.put("verificationRequired", true);
        body.put("challenge", view);
        body.put("rememberMe", remember);
        if (next != null) body.put("next", next);
        return body;
    }

    private static Map<String, Object> view(PolymerUserPrincipal principal) {
        List<String> roles = PolymerUserPrincipal.roleNames(principal.roles());
        return Map.of("id", principal.toUser().userId(), "username", principal.getUsername(),
                "displayName", principal.displayName(), "roles", roles);
    }

    public record LoginRequest(String username, String password, Boolean rememberMe, String next) {
        public LoginRequest(String username, String password) { this(username, password, null, null); }
    }
    public record VerifyRequest(String code, Boolean rememberMe) {}
    public record RegisterRequest(String username, String displayName, String email, String password) {}

    /** ¿Está configurado «Continuar con Google»? (client id y secret presentes). */
    public record GoogleSignInAvailability(boolean enabled) {}
}
