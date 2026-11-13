package com.polymer.config;

import com.polymer.adapter.out.health.DependencyHealthAdapter;
import com.polymer.adapter.out.metrics.MicrometerBusinessTelemetry;
import com.polymer.adapter.out.persistence.JdbcAuditLog;
import com.polymer.adapter.out.persistence.JdbcUserPreferences;
import com.polymer.adapter.out.persistence.JdbcCatalogRepository;
import com.polymer.adapter.out.persistence.JdbcCommerceOrderRepository;
import com.polymer.adapter.out.persistence.JdbcDeliveryRepository;
import com.polymer.adapter.out.persistence.JdbcIncidentRepository;
import com.polymer.adapter.out.persistence.JdbcUserRepository;
import com.polymer.adapter.out.security.BcryptPasswordHasher;
import com.polymer.adapter.out.security.ClasspathPasswordBlocklist;
import com.polymer.application.port.in.AccountUseCases;
import com.polymer.application.port.in.AdministrationUseCases;
import com.polymer.application.port.in.CatalogUseCases;
import com.polymer.application.port.in.CommerceUseCases;
import com.polymer.application.port.in.LogisticsUseCases;
import com.polymer.application.port.in.OperationsUseCases;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CatalogRepository;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.DeliveryRepository;
import com.polymer.application.port.out.IncidentRepository;
import com.polymer.application.port.out.OrderTelemetry;
import com.polymer.application.port.out.PasswordBlocklist;
import com.polymer.application.port.out.UserPreferences;
import com.polymer.application.port.out.PasswordHasher;
import com.polymer.application.port.out.RiskAssessmentPort;
import com.polymer.application.port.out.SystemHealthPort;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.service.AccountService;
import com.polymer.application.service.AdministrationService;
import com.polymer.application.service.CatalogService;
import com.polymer.application.service.CommerceService;
import com.polymer.application.service.LogisticsService;
import com.polymer.application.service.OperationsService;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

/** Composition root of the platform modules (the original HexagonalConfiguration is unchanged). */
@Configuration(proxyBeanMethods = false)
public class PlatformConfiguration {
    private static final Logger LOG = LoggerFactory.getLogger(PlatformConfiguration.class);

    @Bean TransactionTemplate transactionTemplate(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }

    @Bean CatalogRepository catalogRepository(JdbcTemplate jdbc) { return new JdbcCatalogRepository(jdbc); }
    @Bean CommerceOrderRepository commerceOrderRepository(JdbcTemplate jdbc, TransactionTemplate tx) { return new JdbcCommerceOrderRepository(jdbc, tx); }
    @Bean DeliveryRepository deliveryRepository(JdbcTemplate jdbc, TransactionTemplate tx) { return new JdbcDeliveryRepository(jdbc, tx); }
    @Bean IncidentRepository incidentRepository(JdbcTemplate jdbc) { return new JdbcIncidentRepository(jdbc); }
    @Bean UserRepository userRepository(JdbcTemplate jdbc, TransactionTemplate tx) { return new JdbcUserRepository(jdbc, tx); }
    @Bean AuditLog auditLog(JdbcTemplate jdbc) { return new JdbcAuditLog(jdbc); }
    @Bean PasswordHasher passwordHasher(BCryptPasswordEncoder encoder) { return new BcryptPasswordHasher(encoder); }
    @Bean BusinessTelemetry businessTelemetry(MeterRegistry registry) { return new MicrometerBusinessTelemetry(registry); }

    @Bean
    SystemHealthPort systemHealth(JdbcTemplate jdbc, RestClient.Builder builder,
                                  @Value("${RISK_BASE_URL:http://risk-engine:8000}") String riskBaseUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(1));
        requests.setReadTimeout(Duration.ofSeconds(2));
        return new DependencyHealthAdapter(jdbc, builder.baseUrl(riskBaseUrl).requestFactory(requests).build());
    }

    // ---- v6: store (promotions, saved cart) --------------------------------------------------
    @Bean com.polymer.application.port.out.PromotionRepository promotionRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new com.polymer.adapter.out.persistence.JdbcPromotionRepository(jdbc, tx);
    }

    @Bean com.polymer.application.port.out.CartRepository cartRepository(JdbcTemplate jdbc) {
        return new com.polymer.adapter.out.persistence.JdbcCartRepository(jdbc);
    }

    @Bean com.polymer.application.service.PricingService pricingService(com.polymer.application.port.out.PromotionRepository promotions, Clock clock) {
        return new com.polymer.application.service.PricingService(promotions, clock);
    }

    @Bean CatalogUseCases catalogUseCases(CatalogRepository catalog, AuditLog audit, com.polymer.application.port.out.CatalogCache cache,
                                          com.polymer.application.service.PricingService pricing, CommerceOrderRepository orders) {
        return new CatalogService(catalog, audit, cache, pricing, orders);
    }

    @Bean com.polymer.application.port.in.PromotionUseCases promotionUseCases(com.polymer.application.port.out.PromotionRepository promotions,
                                                                                CatalogRepository catalog, com.polymer.application.port.out.CatalogCache cache,
                                                                                AuditLog audit, Clock clock) {
        return new com.polymer.application.service.PromotionService(promotions, catalog, cache, audit, clock);
    }

    @Bean com.polymer.application.port.in.CartUseCases cartUseCases(com.polymer.application.port.out.CartRepository carts, CatalogRepository catalog,
                                                                      com.polymer.application.service.PricingService pricing) {
        return new com.polymer.application.service.CartService(carts, catalog, pricing);
    }

    @Bean
    CommerceUseCases commerceUseCases(CatalogRepository catalog, CommerceOrderRepository orders, DeliveryRepository deliveries,
                                      RiskAssessmentPort risk, OrderTelemetry orderTelemetry, BusinessTelemetry telemetry,
                                      AuditLog audit, Clock clock, com.polymer.application.port.out.CatalogCache cache,
                                      com.polymer.application.service.PricingService pricing,
                                      com.polymer.application.port.out.CartRepository carts) {
        return new CommerceService(catalog, orders, deliveries, risk, orderTelemetry, telemetry, audit, clock, cache, pricing, carts);
    }

    @Bean
    OperationsUseCases operationsUseCases(CommerceOrderRepository orders, DeliveryRepository deliveries, IncidentRepository incidents,
                                          BusinessTelemetry telemetry, AuditLog audit, Clock clock,
                                          com.polymer.application.port.out.CatalogCache cache) {
        return new OperationsService(orders, deliveries, incidents, telemetry, audit, clock, cache);
    }

    @Bean
    LogisticsUseCases logisticsUseCases(DeliveryRepository deliveries, CommerceOrderRepository orders, UserRepository users,
                                        BusinessTelemetry telemetry, AuditLog audit, Clock clock) {
        return new LogisticsService(deliveries, orders, users, telemetry, audit, clock);
    }

    @Bean
    AdministrationUseCases administrationUseCases(CommerceOrderRepository orders, CatalogRepository catalog, IncidentRepository incidents,
                                                  UserRepository users, AuditLog audit, SystemHealthPort health, Clock clock) {
        return new AdministrationService(orders, catalog, incidents, users, audit, health, clock);
    }

    @Bean com.polymer.application.port.out.ContactMessageRepository contactMessageRepository(JdbcTemplate jdbc) {
        return new com.polymer.adapter.out.persistence.JdbcContactMessageRepository(jdbc);
    }

    @Bean com.polymer.application.port.in.ContactUseCases contactUseCases(com.polymer.application.port.out.ContactMessageRepository messages,
                                                                            AuditLog audit, Clock clock) {
        return new com.polymer.application.service.ContactService(messages, audit, clock);
    }

    /** v9 · Testimonios: caso de uso + adaptador JDBC (puerto de salida TestimonialRepository). */
    @Bean com.polymer.application.port.out.TestimonialRepository testimonialRepository(JdbcTemplate jdbc) {
        return new com.polymer.adapter.out.persistence.JdbcTestimonialRepository(jdbc);
    }

    @Bean com.polymer.application.port.in.TestimonialUseCases testimonialUseCases(com.polymer.application.port.out.TestimonialRepository testimonials,
                                                                                    UserRepository users, AuditLog audit, Clock clock) {
        return new com.polymer.application.service.TestimonialService(testimonials, users, audit, clock);
    }

    /** Prometheus is reached through the Docker network (service name), never through localhost. */
    @Bean
    com.polymer.application.port.out.MonitoringPort monitoringPort(RestClient.Builder builder,
                                                                     @Value("${PROMETHEUS_URL:http://prometheus:9090}") String prometheusUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(1));
        requests.setReadTimeout(Duration.ofSeconds(3));
        return new com.polymer.adapter.out.monitoring.PrometheusMonitoringAdapter(builder.baseUrl(prometheusUrl).requestFactory(requests).build());
    }

    @Bean com.polymer.application.port.in.MonitoringUseCases monitoringUseCases(com.polymer.application.port.out.MonitoringPort monitoring, Clock clock) {
        return new com.polymer.application.service.MonitoringService(monitoring, clock);
    }

    @Bean PasswordBlocklist passwordBlocklist() { return new ClasspathPasswordBlocklist(); }
    @Bean UserPreferences userPreferences(JdbcTemplate jdbc) { return new JdbcUserPreferences(jdbc); }
    @Bean AccountUseCases accountUseCases(UserRepository users, PasswordHasher hasher, AuditLog audit, PasswordBlocklist blocklist,
                                          UserPreferences preferences, com.polymer.application.port.out.AccountSecurity security,
                                          SignInConfiguration.MailSenderHolder mail) {
        return new AccountService(users, hasher, audit, blocklist, preferences, security, mail.sender() != null);
    }

    /** Creates the demo accounts once, from the demo_password secret; never modifies existing users. */
    @Bean
    ApplicationRunner demoAccounts(AccountUseCases accounts, @Value("${DEMO_PASSWORD_FILE:/run/secrets/demo_password}") String file) {
        return args -> {
            Path path = Path.of(file);
            if (!Files.isRegularFile(path)) {
                LOG.warn("No existe el secreto {}: no se crean cuentas de demostración", path);
                return;
            }
            int created = accounts.ensureDemoUsers(Files.readString(path, StandardCharsets.UTF_8).strip());
            LOG.info("Cuentas de demostración nuevas: {}", created);
        };
    }

    /**
     * Gauges read from PostgreSQL with a 15-second cache, so a Prometheus scrape never hammers the
     * database. When the database is unavailable the gauges report NaN instead of failing the scrape.
     */
    @Bean
    MeterBinder businessGauges(CommerceOrderRepository orders, CatalogRepository catalog, IncidentRepository incidents) {
        return registry -> {
            Supplier<Map<String, Long>> byStatus = cached(orders::countByStatus);
            for (FulfillmentStatus status : FulfillmentStatus.values()) {
                Gauge.builder("polymer_orders_by_status", () -> {
                    Map<String, Long> snapshot = byStatus.get();
                    return snapshot == null ? Double.NaN : snapshot.getOrDefault(status.name(), 0L);
                }).description("Pedidos por estado del ciclo de vida (PostgreSQL)").tag("status", status.name()).register(registry);
            }
            Supplier<Integer> lowStock = cached(() -> catalog.lowStock(5).size());
            Gauge.builder("polymer_products_low_stock", () -> nan(lowStock.get())).description("Productos activos con stock <= 5").register(registry);
            Supplier<Long> open = cached(incidents::countOpen);
            Gauge.builder("polymer_incidents_open", () -> nan(open.get())).description("Incidencias abiertas").register(registry);
        };
    }

    private static Number nan(Number value) { return value == null ? Double.NaN : value; }

    private static <T> Supplier<T> cached(Supplier<T> source) {
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Long> loadedAt = new AtomicReference<>(0L);
        return () -> {
            long now = System.nanoTime();
            if (now - loadedAt.get() > Duration.ofSeconds(15).toNanos()) {
                try {
                    value.set(source.get());
                } catch (RuntimeException unavailable) {
                    value.set(null);
                }
                loadedAt.set(now);
            }
            return value.get();
        };
    }
}
