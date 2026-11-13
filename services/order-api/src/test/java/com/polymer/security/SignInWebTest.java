package com.polymer.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polymer.adapter.in.web.security.AuthCookies;
import com.polymer.adapter.in.web.security.PolymerUserPrincipal;
import com.polymer.application.port.in.AccountUseCases;
import com.polymer.application.port.in.AdministrationUseCases;
import com.polymer.application.port.in.CatalogUseCases;
import com.polymer.application.port.in.ChallengeView;
import com.polymer.application.port.in.CommerceUseCases;
import com.polymer.application.port.in.LogisticsUseCases;
import com.polymer.application.port.in.OperationsUseCases;
import com.polymer.application.port.in.OrderUseCases;
import com.polymer.application.port.in.PaymentUseCases;
import com.polymer.application.port.in.SignInStep;
import com.polymer.application.port.in.SignInUseCases;
import com.polymer.application.port.in.StoreUseCases;
import com.polymer.application.port.in.TodayView;
import com.polymer.application.port.out.StoredCredentials;
import com.polymer.application.port.out.UserRepository;
import com.polymer.config.SecurityConfiguration;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.identity.VerificationPurpose;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** v7: capa web del inicio de sesión en dos pasos, «Recordarme 30 días» y reglas de acceso de pagos y tienda. */
@WebMvcTest
@Import({SecurityConfiguration.class, SignInWebTest.Metrics.class, SignInTestBeans.class})
class SignInWebTest {
    @TestConfiguration
    static class Metrics {
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    private static final String HASH = new BCryptPasswordEncoder(4).encode("frase segura de prueba");
    private static final UserAccount ANA = new UserAccount(7, "ana", "Ana", "ana@gmail.com", EnumSet.of(Role.CUSTOMER), true, Instant.EPOCH);
    private static final UUID CHALLENGE = UUID.fromString("00000000-0000-4000-8000-0000000000aa");

    @Autowired MockMvc http;
    @MockitoBean OrderUseCases orderUseCases;
    @MockitoBean CatalogUseCases catalog;
    @MockitoBean CommerceUseCases commerce;
    @MockitoBean com.polymer.application.port.in.PromotionUseCases promotions;
    @MockitoBean com.polymer.application.port.in.CartUseCases carts;
    @MockitoBean AccountUseCases accounts;
    @MockitoBean SignInUseCases signIn;
    @MockitoBean PaymentUseCases payments;
    @MockitoBean StoreUseCases store;
    @MockitoBean AdministrationUseCases administration;
    @MockitoBean OperationsUseCases operations;
    @MockitoBean LogisticsUseCases logistics;
    @MockitoBean UserRepository users;
    @MockitoBean com.polymer.application.port.in.ContactUseCases contact;
    @MockitoBean com.polymer.application.port.in.MonitoringUseCases monitoring;

    @BeforeEach
    void setUp() {
        when(users.credentials("ana")).thenReturn(Optional.of(new StoredCredentials(ANA, HASH)));
        when(signIn.account(7)).thenReturn(ANA);
        when(signIn.verificationEnabled()).thenReturn(true);
    }

    private static ChallengeView challenge() {
        return new ChallengeView(CHALLENGE, VerificationPurpose.SIGN_IN, "an•••@gmail.com", Instant.parse("2026-11-13T15:10:00Z"), 5, 60, false);
    }

    @Test
    void theCodeCompletesTheSignInAndRememberMeSetsThirtyDayCookies() throws Exception {
        when(signIn.afterPassword(eq(7L), any())).thenReturn(new SignInStep(7, challenge(), false));
        when(signIn.verify(CHALLENGE, "123456")).thenReturn(SignInStep.complete(7));
        when(signIn.rememberLogin(7)).thenReturn("serieserieserie01.fichafichafichafichafichafichaficha01");
        when(signIn.trustDevice(7)).thenReturn("dispositivodispo01.fichafichafichafichafichafichaficha02");
        MockHttpSession session = new MockHttpSession();
        http.perform(post("/api/auth/login").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"frase segura de prueba\",\"rememberMe\":true,\"next\":\"/checkout.html\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.challenge.email").value("an•••@gmail.com"))
                .andExpect(jsonPath("$.challenge.digits").value(6));
        // Sin el código, la sesión no da acceso a nada.
        http.perform(get("/api/auth/me").session(session)).andExpect(jsonPath("$.authenticated").value(false));
        http.perform(get("/api/shop/orders").session(session)).andExpect(status().isUnauthorized());
        http.perform(post("/api/auth/verify").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.user.username").value("ana"))
                .andExpect(jsonPath("$.next").value("/checkout.html"))
                .andExpect(cookie().maxAge(AuthCookies.REMEMBER, 30 * 24 * 3600))
                .andExpect(cookie().httpOnly(AuthCookies.REMEMBER, true))
                .andExpect(cookie().exists(AuthCookies.DEVICE));
    }

    @Test
    void withoutVerificationThePasswordOpensTheSessionDirectly() throws Exception {
        when(signIn.afterPassword(eq(7L), any())).thenReturn(SignInStep.complete(7));
        http.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ana\",\"password\":\"frase segura de prueba\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(cookie().doesNotExist(AuthCookies.REMEMBER));
        http.perform(post("/api/auth/verify").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRememberedLoginReopensTheSessionAndRotatesTheCookie() throws Exception {
        when(signIn.resume("serieserieserie01.viejaviejaviejaviejaviejaviejavieja1"))
                .thenReturn(Optional.of(new SignInUseCases.ResumedLogin(7, "serieserieserie01.nuevanuevanuevanuevanuevanuevanueva1")));
        http.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.REMEMBER, "serieserieserie01.viejaviejaviejaviejaviejaviejavieja1")))
                .andExpect(jsonPath("$.authenticated").value(true)).andExpect(jsonPath("$.user.username").value("ana"))
                .andExpect(cookie().value(AuthCookies.REMEMBER, "serieserieserie01.nuevanuevanuevanuevanuevanuevanueva1"));
        http.perform(get("/api/auth/me").cookie(new Cookie(AuthCookies.REMEMBER, "robada.robadarobadarobadarobadarobada")))
                .andExpect(jsonPath("$.authenticated").value(false)).andExpect(cookie().maxAge(AuthCookies.REMEMBER, 0));
    }

    @Test
    void providersAndStoreArePublicPaymentsNeedACustomerAndGoogleIsOffWithoutCredentials() throws Exception {
        http.perform(get("/api/auth/providers")).andExpect(status().isOk()).andExpect(jsonPath("$.google").value(false))
                .andExpect(jsonPath("$.emailVerification").value(true)).andExpect(jsonPath("$.rememberDays").value(30));
        when(store.today()).thenReturn(new TodayView("2026-11-13", 5, "10:00", true, "09:00", "19:00", null, null, Instant.EPOCH, "https://calendar.google.com/x"));
        http.perform(get("/api/store/today")).andExpect(status().isOk()).andExpect(jsonPath("$.openNow").value(true));
        UUID order = UUID.randomUUID();
        http.perform(get("/api/shop/orders/" + order + "/payment")).andExpect(status().isUnauthorized());
        var admin = new PolymerUserPrincipal(1, "admin", "Admin", null, EnumSet.of(Role.ADMIN), true);
        http.perform(get("/api/shop/orders/" + order + "/payment")
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()))))
                .andExpect(status().isForbidden());
        http.perform(post("/api/shop/orders/" + order + "/payment/card").contentType(MediaType.APPLICATION_JSON).content("{}")
                .with(authentication(UsernamePasswordAuthenticationToken.authenticated(admin, null, admin.getAuthorities()))))
                .andExpect(status().isForbidden()); // sin CSRF y sin rol
        http.perform(get("/api/auth/google")).andExpect(redirectedUrl("/login.html?google=off"));
        http.perform(get("/api/store")).andExpect(header().string("Content-Security-Policy",
                org.hamcrest.Matchers.containsString("frame-src https://www.google.com")));
        when(signIn.afterPassword(anyLong(), any())).thenReturn(SignInStep.complete(7));
    }
}
