package com.hexaorders.adapter.in.web;

import com.hexaorders.adapter.in.web.security.HexaUserPrincipal;
import com.hexaorders.adapter.in.web.security.SecurityTelemetry;
import com.hexaorders.application.port.in.AccountUseCases;
import com.hexaorders.domain.identity.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Session-based authentication for the SPA (HttpOnly cookie + CSRF token header). */
@RestController
@RequestMapping("/api/auth")
public final class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final AccountUseCases accounts;
    private final SecurityTelemetry telemetry;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                          AccountUseCases accounts, SecurityTelemetry telemetry) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.accounts = accounts;
        this.telemetry = telemetry;
    }

    /** The SPA reads the token here and sends it back in the X-CSRF-TOKEN header on every write. */
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof HexaUserPrincipal principal) {
            return Map.of("authenticated", true, "user", view(principal));
        }
        return Map.of("authenticated", false);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String username = request.username() == null ? "" : request.username().strip();
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password() == null ? "" : request.password()));
            HexaUserPrincipal principal = ((HexaUserPrincipal) authentication.getPrincipal()).withoutCredentials();
            HttpSession existing = httpRequest.getSession(false);
            if (existing != null) httpRequest.changeSessionId(); // session fixation protection
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
            SecurityContextHolder.setContext(context);
            contextRepository.saveContext(context, httpRequest, httpResponse);
            telemetry.login(true);
            accounts.recordAuthentication(principal.getUsername(), true);
            return ResponseEntity.ok(Map.of("authenticated", true, "user", view(principal)));
        } catch (AuthenticationException failed) {
            telemetry.login(false);
            accounts.recordAuthentication(username, false);
            ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
            problem.setTitle("Credenciales no válidas");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(problem);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
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
                "roles", HexaUserPrincipal.roleNames(created.roles())));
    }

    private static Map<String, Object> view(HexaUserPrincipal principal) {
        List<String> roles = HexaUserPrincipal.roleNames(principal.roles());
        return Map.of("id", principal.toUser().userId(), "username", principal.getUsername(),
                "displayName", principal.displayName(), "roles", roles);
    }

    public record LoginRequest(String username, String password) {}
    public record RegisterRequest(String username, String displayName, String email, String password) {}
}
