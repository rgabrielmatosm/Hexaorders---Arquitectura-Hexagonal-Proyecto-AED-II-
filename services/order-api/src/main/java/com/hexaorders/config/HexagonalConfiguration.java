package com.hexaorders.config;

import com.hexaorders.adapter.out.http.HttpRiskAssessmentAdapter;
import com.hexaorders.adapter.out.metrics.MicrometerOrderTelemetry;
import com.hexaorders.adapter.out.persistence.JdbcOrderRepository;
import com.hexaorders.application.port.in.OrderUseCases;
import com.hexaorders.application.port.out.OrderRepository;
import com.hexaorders.application.port.out.OrderTelemetry;
import com.hexaorders.application.port.out.RiskAssessmentPort;
import com.hexaorders.application.service.OrderService;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** The composition root is the only place that selects concrete outbound adapters. */
@Configuration(proxyBeanMethods = false)
public class HexagonalConfiguration {
    @Bean(destroyMethod = "close")
    DataSource dataSource(@Value("${DB_HOST:postgres}") String host,
                          @Value("${DB_PORT:5432}") int port,
                          @Value("${DB_NAME:hexaorders}") String database,
                          @Value("${DB_USER:hexaorders}") String user,
                          @Value("${DB_PASSWORD_FILE:/run/secrets/db_password}") String passwordFile) throws IOException {
        String password = Files.readString(Path.of(passwordFile), StandardCharsets.UTF_8).strip();
        if (password.isEmpty()) { throw new IllegalStateException("El secreto de base de datos está vacío"); }
        HikariDataSource source = new HikariDataSource();
        source.setJdbcUrl("jdbc:postgresql://" + host + ":" + port + "/" + database
                + "?connectTimeout=3&socketTimeout=5&ApplicationName=hexaorders-api");
        source.setUsername(user);
        source.setPassword(password);
        source.setMaximumPoolSize(10);
        source.setMinimumIdle(2);
        source.setConnectionTimeout(3000);
        source.setValidationTimeout(1000);
        source.setPoolName("orders-pool");
        return source;
    }

    @Bean
    OrderRepository orderRepository(JdbcTemplate jdbc) { return new JdbcOrderRepository(jdbc); }

    @Bean
    RiskAssessmentPort riskAssessmentPort(RestClient.Builder builder,
                                         @Value("${RISK_BASE_URL:http://risk-engine:8000}") String baseUrl) {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(2));
        requests.setReadTimeout(Duration.ofSeconds(3));
        return new HttpRiskAssessmentAdapter(builder.baseUrl(baseUrl).requestFactory(requests).build());
    }

    @Bean
    OrderTelemetry orderTelemetry(MeterRegistry registry) { return new MicrometerOrderTelemetry(registry); }

    @Bean
    Clock clock() { return Clock.systemUTC(); }

    @Bean
    OrderUseCases orderUseCases(OrderRepository repository, RiskAssessmentPort risk,
                               OrderTelemetry telemetry, Clock clock) {
        return new OrderService(repository, risk, telemetry, clock);
    }
}
