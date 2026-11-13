package com.polymer.adapter.in.web.i18n;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.polymer.adapter.in.web.security.PolymerUserPrincipal;
import com.polymer.application.port.in.AccountUseCases;
import com.polymer.application.port.in.AdministrationUseCases;
import com.polymer.application.port.in.CatalogUseCases;
import com.polymer.application.port.in.CommerceUseCases;
import com.polymer.application.port.in.LogisticsUseCases;
import com.polymer.application.port.in.OperationsUseCases;
import com.polymer.application.port.in.OrderUseCases;
import com.polymer.application.port.out.UserRepository;
import com.polymer.config.SecurityConfiguration;
import com.polymer.domain.identity.Role;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Rutas localizadas /es … /de: SEO, permisos con la cadena de seguridad real, 404 y sitemap multilingüe. */
@WebMvcTest
@Import({SecurityConfiguration.class, LocalizedPagesTest.Metrics.class, com.polymer.security.SignInTestBeans.class})
class LocalizedPagesTest {
    @TestConfiguration
    static class Metrics {
        @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
    }

    @Autowired MockMvc http;
    @MockitoBean OrderUseCases orderUseCases;
    @MockitoBean CatalogUseCases catalog;
    @MockitoBean CommerceUseCases commerce;
    @MockitoBean com.polymer.application.port.in.PromotionUseCases promotions;
    @MockitoBean com.polymer.application.port.in.CartUseCases carts;
    @MockitoBean AccountUseCases accounts;
    @MockitoBean com.polymer.application.port.in.SignInUseCases signIn;
    @MockitoBean com.polymer.application.port.in.PaymentUseCases payments;
    @MockitoBean com.polymer.application.port.in.StoreUseCases store;
    @MockitoBean com.polymer.application.port.in.TestimonialUseCases testimonials;
    @MockitoBean AdministrationUseCases administration;
    @MockitoBean OperationsUseCases operations;
    @MockitoBean LogisticsUseCases logistics;
    @MockitoBean UserRepository users;
    @MockitoBean com.polymer.application.port.in.ContactUseCases contact;
    @MockitoBean com.polymer.application.port.in.MonitoringUseCases monitoring;

    private static RequestPostProcessor as(Role role) {
        var principal = new PolymerUserPrincipal(5, "user", "Usuario", null, EnumSet.of(role), true);
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }

    @Test
    void publicPageIsServedInTheRequestedLanguageWithSeoLinks() throws Exception {
        http.perform(get("/fr/produits"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", "fr"))
                .andExpect(content().string(allOf(
                        containsString("<html lang=\"fr\">"),
                        containsString("<title>Catalogue · POLYMER</title>"),
                        containsString("<link rel=\"canonical\" href=\"http://localhost/fr/produits\">"),
                        containsString("hreflang=\"es\" href=\"http://localhost/es/productos\""),
                        containsString("hreflang=\"de\" href=\"http://localhost/de/produkte\""),
                        containsString("hreflang=\"x-default\" href=\"http://localhost/es/productos\""),
                        containsString("Aller au contenu"),
                        not(containsString("Saltar al contenido")))));
    }

    @Test
    void spanishIsTheDefaultAndKeepsTheOriginalTexts() throws Exception {
        http.perform(get("/es/productos"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("<html lang=\"es\">"), containsString("<title>Catálogo · POLYMER</title>"),
                        containsString("Saltar al contenido"))));
    }

    @Test
    void protectedPageRedirectsVisitorsToTheLocalizedLogin() throws Exception {
        http.perform(get("/it/cassa"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/it/accedi?next=%2Fit%2Fcassa"));
    }

    @Test
    void pageOfAnotherRoleIsALocalizedForbidden() throws Exception {
        http.perform(get("/en/admin").with(as(Role.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(content().string(allOf(containsString("<html lang=\"en\">"), containsString("noindex"))));
        http.perform(get("/en/admin").with(as(Role.ADMIN))).andExpect(status().isOk());
    }

    @Test
    void unknownSlugOrLanguageIsNotFound() throws Exception {
        http.perform(get("/de/gibt-es-nicht"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("<html lang=\"de\">")));
        http.perform(get("/xx/products")).andExpect(status().isNotFound());
    }

    @Test
    void sitemapListsEveryLanguageButNotInternalPages() throws Exception {
        http.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(
                        containsString("<loc>http://localhost/en/products</loc>"),
                        containsString("hreflang=\"pt\" href=\"http://localhost/pt/produtos\""),
                        not(containsString("/admin")),
                        not(containsString("/checkout")))));
    }
}
