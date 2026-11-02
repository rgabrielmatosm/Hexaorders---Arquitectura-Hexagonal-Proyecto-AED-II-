package com.hexaorders.config;

import com.hexaorders.adapter.in.web.security.HexaUserPrincipal;
import com.hexaorders.adapter.in.web.security.SecurityTelemetry;
import com.hexaorders.application.port.out.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Authentication and role-based authorization (RBAC). Rules are enforced here per URL and again
 * inside every use case (defense in depth). The original public API /api/orders keeps its
 * behaviour (no login, no CSRF) for compatibility with the existing clients, tests and docs.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfiguration {
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; "
            + "connect-src 'self' http://*:9091 https://*:9091 http://*:3000 https://*:3000; font-src 'self'; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    @Bean
    BCryptPasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return username -> users.credentials(username)
                .map(c -> new HexaUserPrincipal(c.account().id(), c.account().username(), c.account().displayName(),
                        c.passwordHash(), c.account().roles(), c.account().enabled()))
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
    }

    @Bean
    @SuppressWarnings("deprecation")
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, BCryptPasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(encoder);
        return new ProviderManager(provider);
    }

    @Bean
    SecurityContextRepository securityContextRepository() { return new HttpSessionSecurityContextRepository(); }

    @Bean
    SecurityTelemetry securityTelemetry(MeterRegistry registry) { return new SecurityTelemetry(registry); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
                                            SecurityTelemetry telemetry) throws Exception {
        http
            .securityContext(context -> context.securityContextRepository(contextRepository))
            // SPA: the token is read from GET /api/auth/csrf and sent in the X-CSRF-TOKEN header.
            .csrf(csrf -> csrf.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    .ignoringRequestMatchers("/api/orders", "/api/orders/**"))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/orders", "/api/orders/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/catalog/**").permitAll()
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/shop/quote").permitAll()
                    .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                    .requestMatchers("/api/shop/**").hasRole("CUSTOMER")
                    .requestMatchers("/api/account/**").authenticated()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .requestMatchers("/api/operations/**").hasAnyRole("ORDER_OPERATOR", "ADMIN")
                    .requestMatchers("/api/logistics/**").hasAnyRole("LOGISTICS_OPERATOR", "ADMIN")
                    .requestMatchers("/api/incidents/**").hasAnyRole("ORDER_OPERATOR", "LOGISTICS_OPERATOR", "ADMIN")
                    .requestMatchers("/api/ops/**").hasAnyRole("OBSERVABILITY_VIEWER", "ADMIN")
                    // Actuator lives on the management port 9090, reachable only from internal Docker networks.
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/api/**").denyAll()
                    // v4 multipage site: protected HTML pages are checked on the server too, not only hidden in menus.
                    .requestMatchers("/checkout.html").hasRole("CUSTOMER")
                    .requestMatchers("/cliente/perfil.html").authenticated()
                    .requestMatchers("/cliente", "/cliente/**").hasRole("CUSTOMER")
                    .requestMatchers("/admin", "/admin/**").hasRole("ADMIN")
                    .requestMatchers("/operaciones", "/operaciones/**").hasAnyRole("ORDER_OPERATOR", "ADMIN")
                    .requestMatchers("/logistica", "/logistica/**").hasAnyRole("LOGISTICS_OPERATOR", "ADMIN")
                    .requestMatchers("/monitoreo", "/monitoreo/**").hasAnyRole("OBSERVABILITY_VIEWER", "ADMIN")
                    .anyRequest().permitAll())
            .exceptionHandling(errors -> errors
                    .authenticationEntryPoint((request, response, failure) -> {
                        telemetry.unauthenticated();
                        if (isPage(request)) {
                            // Pages: back to the login page, which returns here after signing in.
                            response.sendRedirect("/login.html?next=" + URLEncoder.encode(pathWithQuery(request), StandardCharsets.UTF_8));
                            return;
                        }
                        problem(response, HttpServletResponse.SC_UNAUTHORIZED, "Autenticación requerida", "Inicia sesión para continuar");
                    })
                    .accessDeniedHandler((request, response, denied) -> {
                        telemetry.forbidden();
                        if (isPage(request) && !(denied instanceof CsrfException)) {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            request.getRequestDispatcher("/403.html").forward(request, response);
                            return;
                        }
                        if (denied instanceof CsrfException) {
                            problem(response, HttpServletResponse.SC_FORBIDDEN, "Token CSRF no válido", "Recarga la página e inténtalo de nuevo");
                        } else {
                            problem(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado", "Tu rol no permite esta operación");
                        }
                    }))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)));
        return http.build();
    }

    private static boolean isPage(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/") && !path.startsWith("/actuator/");
    }

    private static String pathWithQuery(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return request.getQueryString() == null ? path : path + "?" + request.getQueryString();
    }

    private static void problem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }
}
