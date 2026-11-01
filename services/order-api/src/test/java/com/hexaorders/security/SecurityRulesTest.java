package com.hexaorders.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hexaorders.adapter.in.web.security.HexaUserPrincipal;
import com.hexaorders.application.port.in.AccountUseCases;
import com.hexaorders.application.port.in.AdminDashboard;
import com.hexaorders.application.port.in.AdministrationUseCases;
import com.hexaorders.application.port.in.CatalogUseCases;
import com.hexaorders.application.port.in.CommerceUseCases;
import com.hexaorders.application.port.in.LogisticsUseCases;
import com.hexaorders.application.port.in.OperationsUseCases;
import com.hexaorders.application.port.in.SystemHealth;
import com.hexaorders.application.port.in.OrderUseCases;
import com.hexaorders.application.port.out.UserRepository;
import com.hexaorders.config.SecurityConfiguration;
import com.hexaorders.domain.OrderStatistics;
import com.hexaorders.domain.identity.Role;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** RBAC, CSRF and compatibility of the original public API, exercised through the real security filter chain. */
@WebMvcTest
@Import({SecurityConfiguration.class, SecurityRulesTest.Metrics.class})
class SecurityRulesTest {
    @TestConfiguration
    static class Metrics {
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    @Autowired MockMvc http;
    @MockitoBean OrderUseCases orderUseCases;
    @MockitoBean CatalogUseCases catalog;
    @MockitoBean CommerceUseCases commerce;
    @MockitoBean AccountUseCases accounts;
    @MockitoBean AdministrationUseCases administration;
    @MockitoBean OperationsUseCases operations;
    @MockitoBean LogisticsUseCases logistics;
    @MockitoBean UserRepository users;

    private static RequestPostProcessor as(Role... roles) {
        var principal = new HexaUserPrincipal(5, "user", "Usuario", null, EnumSet.of(roles[0], roles), true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void originalPublicApiStillWorksWithoutLoginOrCsrf() throws Exception {
        when(orderUseCases.statistics()).thenReturn(OrderStatistics.of(1, 0));
        http.perform(get("/api/orders/statistics")).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(1));
        http.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"customerId\":\"demo\",\"amount\":0}"))
                .andExpect(status().isBadRequest()); // reaches the domain validation: not blocked by security
    }

    @Test
    void staticFrontendAndCatalogArePublicAndCspIsSent() throws Exception {
        when(catalog.products(any(), any())).thenReturn(List.of());
        http.perform(get("/api/catalog/products")).andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", org.hamcrest.Matchers.containsString("frame-ancestors 'none'")));
    }

    @Test
    void anonymousUsersGet401OnProtectedEndpoints() throws Exception {
        http.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        http.perform(get("/api/shop/orders")).andExpect(status().isUnauthorized());
        http.perform(get("/api/ops/health")).andExpect(status().isUnauthorized());
        http.perform(get("/api/unknown")).andExpect(status().isUnauthorized());
    }

    @Test
    void eachRoleOnlyReachesItsArea() throws Exception {
        when(administration.dashboard(any())).thenReturn(new AdminDashboard(0, Map.of(), BigDecimal.ZERO, 0, 0, List.of(), 0, Map.of(), List.of(), List.of()));
        when(administration.health(any())).thenReturn(new SystemHealth(true, true, Map.of(), 0, Instant.now()));
        http.perform(get("/api/admin/dashboard").with(as(Role.CUSTOMER))).andExpect(status().isForbidden());
        http.perform(get("/api/admin/dashboard").with(as(Role.ORDER_OPERATOR))).andExpect(status().isForbidden());
        http.perform(get("/api/admin/dashboard").with(as(Role.ADMIN))).andExpect(status().isOk());
        http.perform(get("/api/operations/orders").with(as(Role.LOGISTICS_OPERATOR))).andExpect(status().isForbidden());
        http.perform(get("/api/operations/orders").with(as(Role.ORDER_OPERATOR))).andExpect(status().isOk());
        http.perform(get("/api/logistics/assignees").with(as(Role.ORDER_OPERATOR))).andExpect(status().isForbidden());
        http.perform(get("/api/logistics/assignees").with(as(Role.LOGISTICS_OPERATOR))).andExpect(status().isOk());
        http.perform(get("/api/ops/health").with(as(Role.OBSERVABILITY_VIEWER))).andExpect(status().isOk());
        http.perform(get("/api/admin/users").with(as(Role.OBSERVABILITY_VIEWER))).andExpect(status().isForbidden());
        http.perform(get("/api/shop/orders").with(as(Role.ADMIN))).andExpect(status().isForbidden());
        http.perform(get("/api/shop/orders").with(as(Role.CUSTOMER))).andExpect(status().isOk());
    }

    @Test
    void writesRequireTheCsrfToken() throws Exception {
        String body = "{\"items\":[{\"productId\":1,\"quantity\":1}]}";
        http.perform(post("/api/shop/orders").with(as(Role.CUSTOMER)).header("Idempotency-Key", "key-12345678")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.title").value("Token CSRF no válido"));
        http.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        http.perform(post("/api/shop/quote").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void csrfEndpointAndMeArePublic() throws Exception {
        http.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));
        http.perform(get("/api/auth/me")).andExpect(status().isOk()).andExpect(jsonPath("$.authenticated").value(false));
    }
}
