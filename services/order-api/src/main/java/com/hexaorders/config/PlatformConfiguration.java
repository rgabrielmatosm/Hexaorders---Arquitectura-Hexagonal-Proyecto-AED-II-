package com.hexaorders.config;

import com.hexaorders.adapter.out.health.DependencyHealthAdapter;
import com.hexaorders.adapter.out.metrics.MicrometerBusinessTelemetry;
import com.hexaorders.adapter.out.persistence.JdbcAuditLog;
import com.hexaorders.adapter.out.persistence.JdbcCatalogRepository;
import com.hexaorders.adapter.out.persistence.JdbcCommerceOrderRepository;
import com.hexaorders.adapter.out.persistence.JdbcDeliveryRepository;
import com.hexaorders.adapter.out.persistence.JdbcIncidentRepository;
import com.hexaorders.adapter.out.persistence.JdbcUserRepository;
import com.hexaorders.adapter.out.security.BcryptPasswordHasher;
import com.hexaorders.application.port.in.AccountUseCases;
import com.hexaorders.application.port.in.AdministrationUseCases;
import com.hexaorders.application.port.in.CatalogUseCases;
import com.hexaorders.application.port.in.CommerceUseCases;
import com.hexaorders.application.port.in.LogisticsUseCases;
import com.hexaorders.application.port.in.OperationsUseCases;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.BusinessTelemetry;
import com.hexaorders.application.port.out.CatalogRepository;
import com.hexaorders.application.port.out.CommerceOrderRepository;
import com.hexaorders.application.port.out.DeliveryRepository;
import com.hexaorders.application.port.out.IncidentRepository;
import com.hexaorders.application.port.out.OrderTelemetry;
import com.hexaorders.application.port.out.PasswordHasher;
import com.hexaorders.application.port.out.RiskAssessmentPort;
import com.hexaorders.application.port.out.SystemHealthPort;
import com.hexaorders.application.port.out.UserRepository;
import com.hexaorders.application.service.AccountService;
import com.hexaorders.application.service.AdministrationService;
import com.hexaorders.application.service.CatalogService;
import com.hexaorders.application.service.CommerceService;
import com.hexaorders.application.service.LogisticsService;
import com.hexaorders.application.service.OperationsService;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
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
    @Bean com.hexaorders.application.port.out.PromotionRepository promotionRepository(JdbcTemplate jdbc, TransactionTemplate tx) {
        return new com.hexaorders.adapter.out.persistence.JdbcPromotionRepository(jdbc, tx);
    }

    @Bean com.hexaorders.application.port.out.CartRepository cartRepository(JdbcTemplate jdbc) {
        return new com.hexaorders.adapter.out.persistence.JdbcCartRepository(jdbc);
    }

    @Bean com.hexaorders.application.service.PricingService pricingService(com.hexaorders.application.port.out.PromotionRepository promotions, Clock clock) {
        return new com.hexaorders.application.service.PricingService(promotions, clock);
    }

    @Bean CatalogUseCases catalogUseCases(CatalogRepository catalog, AuditLog audit, com.hexaorders.application.port.out.CatalogCache cache,
                                          com.hexaorders.application.service.PricingService pricing, CommerceOrderRepository orders) {
        return new CatalogService(catalog, audit, cache, pricing, orders);
    }

    @Bean com.hexaorders.application.port.in.PromotionUseCases promotionUseCases(com.hexaorders.application.port.out.PromotionRepository promotions,
                                                                                CatalogRepository catalog, com.hexaorders.application.port.out.CatalogCache cache,
                                                                                AuditLog audit, Clock clock) {
        return new com.hexaorders.application.service.PromotionService(promotions, catalog, cache, audit, clock);
    }

    @Bean com.hexaorders.application.port.in.CartUseCases cartUseCases(com.hexaorders.application.port.out.CartRepository carts, CatalogRepository catalog,
                                                                      com.hexaorders.application.service.PricingService pricing) {
        return new com.hexaorders.application.service.CartService(carts, catalog, pricing);
    }

    @Bean
    CommerceUseCases commerceUseCases(CatalogRepository catalog, CommerceOrderRepository orders, DeliveryRepository deliveries,
                                      RiskAssessmentPort risk, OrderTelemetry orderTelemetry, BusinessTelemetry telemetry,
                                      AuditLog audit, Clock clock, com.hexaorders.application.port.out.CatalogCache cache,
                                      com.hexaorders.application.service.PricingService pricing,
                                      com.hexaorders.application.port.out.CartRepository carts) {
        return new CommerceService(catalog, orders, deliveries, risk, orderTelemetry, telemetry, audit, clock, cache, pricing, carts);
    }

    @Bean
    OperationsUseCases operationsUseCases(CommerceOrderRepository orders, DeliveryRepository deliveries, IncidentRepository incidents,
                                          BusinessTelemetry telemetry, AuditLog audit, Clock clock,
                                          com.hexaorders.application.port.out.CatalogCache cache) {
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

    @Bean com.hexaorders.application.port.out.ContactMessageRepository contactMessageRepository(JdbcTemplate jdbc) {
        return new com.hexaorders.adapter.out.persistence.JdbcContactMessageRepository(jdbc);
    }

    @Bean com.hexaorders.application.port.in.ContactUseCases contactUseCases(com.hexaorders.application.port.out.ContactMessageRepository messages,
                                                                            AuditLog audit, Clock clock) {
        return new com.hexaorders.application.service.ContactService(messages, audit, clock);
    }

    /** Prometheus is reached through the Docker network (service name), never through localhost. */
    @Bean
    com.hexaorders.application.port.out.MonitoringPort monitoringPort(RestClient.Builder builder,
                                                                     @Value("${PROMETHEUS_URL:http://prometheus:9090}") String prometheusUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(1));
        requests.setReadTimeout(Duration.ofSeconds(3));
        return new com.hexaorders.adapter.out.monitoring.PrometheusMonitoringAdapter(builder.baseUrl(prometheusUrl).requestFactory(requests).build());
    }

    @Bean com.hexaorders.application.port.in.MonitoringUseCases monitoringUseCases(com.hexaorders.application.port.out.MonitoringPort monitoring, Clock clock) {
        return new com.hexaorders.application.service.MonitoringService(monitoring, clock);
    }

    @Bean AccountUseCases accountUseCases(UserRepository users, PasswordHasher hasher, AuditLog audit) { return new AccountService(users, hasher, audit); }

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
                Gauge.builder("hexaorders_orders_by_status", () -> {
                    Map<String, Long> snapshot = byStatus.get();
                    return snapshot == null ? Double.NaN : snapshot.getOrDefault(status.name(), 0L);
                }).description("Pedidos por estado del ciclo de vida (PostgreSQL)").tag("status", status.name()).register(registry);
            }
            Supplier<Integer> lowStock = cached(() -> catalog.lowStock(5).size());
            Gauge.builder("hexaorders_products_low_stock", () -> nan(lowStock.get())).description("Productos activos con stock <= 5").register(registry);
            Supplier<Long> open = cached(incidents::countOpen);
            Gauge.builder("hexaorders_incidents_open", () -> nan(open.get())).description("Incidencias abiertas").register(registry);
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
