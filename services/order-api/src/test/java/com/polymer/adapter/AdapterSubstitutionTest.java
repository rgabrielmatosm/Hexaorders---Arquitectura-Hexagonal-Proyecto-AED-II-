package com.polymer.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.polymer.adapter.out.fixed.FixedRiskAssessmentAdapter;
import com.polymer.application.port.out.OrderRepository;
import com.polymer.application.port.out.OrderTelemetry;
import com.polymer.application.port.out.RiskAssessmentPort;
import com.polymer.application.service.OrderService;
import com.polymer.config.HexagonalConfiguration;
import com.polymer.domain.Order;
import com.polymer.domain.OrderDraft;
import com.polymer.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/**
 * Inversión de dependencias: el MISMO caso de uso (OrderService) funciona con dos adaptadores distintos
 * del puerto RiskAssessmentPort, y la raíz de composición elige uno u otro solo por configuración.
 */
class AdapterSubstitutionTest {
    private final Instant now = Instant.parse("2026-01-01T12:00:00Z");
    private final OrderDraft draft = new OrderDraft("cliente_demo", new BigDecimal("1500.00"), "USD");

    @Test
    void theUseCaseWorksUnchangedWithTheFixedAdapter() {
        OrderRepository repository = mock(OrderRepository.class);
        OrderService service = new OrderService(repository, new FixedRiskAssessmentAdapter(10),
                mock(OrderTelemetry.class), Clock.fixed(now, ZoneOffset.UTC));
        Order order = service.create(draft);
        // Con el adaptador HTTP real, 1500 USD daría 75 (REVIEW); con el fijo, 10: el dominio decide APPROVED.
        assertThat(order.riskScore()).isEqualTo(10);
        assertThat(order.status()).isEqualTo(OrderStatus.APPROVED);
        verify(repository).save(order);
    }

    @Test
    void theFixedAdapterRejectsScoresOutsideTheDomainRange() {
        assertThatThrownBy(() -> new FixedRiskAssessmentAdapter(101)).isInstanceOf(IllegalArgumentException.class);
    }

    private final ApplicationContextRunner composition = new ApplicationContextRunner()
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withUserConfiguration(HexagonalConfiguration.class)
            .withBean(org.springframework.jdbc.core.JdbcTemplate.class, () -> mock(org.springframework.jdbc.core.JdbcTemplate.class))
            .withBean(io.micrometer.core.instrument.MeterRegistry.class, io.micrometer.core.instrument.simple.SimpleMeterRegistry::new)
            // La raíz de composición también declara el DataSource (perezoso: no se conecta en esta prueba).
            .withPropertyValues("DB_PASSWORD_FILE=" + passwordFile(), "DB_HOST=127.0.0.1");

    private static String passwordFile() {
        try {
            java.nio.file.Path file = java.nio.file.Files.createTempFile("polymer-test-db", ".txt");
            java.nio.file.Files.writeString(file, "solo-para-esta-prueba");
            file.toFile().deleteOnExit();
            return file.toString();
        } catch (java.io.IOException error) {
            throw new java.io.UncheckedIOException(error);
        }
    }

    @Test
    void byDefaultTheCompositionRootWiresTheRealHttpAdapter() {
        composition.run(context -> assertThat(context.getBean(RiskAssessmentPort.class).getClass().getSimpleName())
                .isEqualTo("HttpRiskAssessmentAdapter"));
    }

    @Test
    void theDemoPropertySwapsTheAdapterWithoutTouchingTheUseCase() {
        composition.withPropertyValues("polymer.risk.adapter=fixed", "polymer.risk.fixed-score=25")
                .run(context -> {
                    assertThat(context.getBean(RiskAssessmentPort.class)).isInstanceOf(FixedRiskAssessmentAdapter.class);
                    assertThat(context.getBean(RiskAssessmentPort.class).assess(draft)).isEqualTo(25);
                });
    }
}
