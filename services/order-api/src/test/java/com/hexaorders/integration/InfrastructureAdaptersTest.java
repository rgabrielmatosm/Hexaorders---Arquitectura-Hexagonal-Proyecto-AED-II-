package com.hexaorders.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hexaorders.adapter.messaging.EventContract;
import com.hexaorders.adapter.out.cache.RedisCatalogCache;
import com.hexaorders.adapter.out.messaging.RabbitMqEventPublisher;
import com.hexaorders.application.exception.EventPublishingException;
import com.hexaorders.config.IntegrationEnvironment;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.event.BusinessEvent;
import com.hexaorders.domain.fulfillment.FulfillmentStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.SpringApplication;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;

class InfrastructureAdaptersTest {
    private final BusinessEvent event = BusinessEvent.fromStatusChange(5, UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
            FulfillmentStatus.CREATED, FulfillmentStatus.RISK_APPROVED, Instant.parse("2026-11-02T10:00:00Z"));

    @Test
    void theWireContractRoundTripsAndRejectsUnknownVersions() {
        byte[] body = EventContract.encode(event);
        assertThat(new String(body, StandardCharsets.UTF_8)).contains("\"routingKey\":\"order.approved\"").doesNotContain("@");
        assertThat(EventContract.decode(body)).isEqualTo(event);
        byte[] future = new String(body, StandardCharsets.UTF_8).replace("\"schemaVersion\":1", "\"schemaVersion\":2").getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> EventContract.decode(future)).hasMessageContaining("schemaVersion");
        assertThatThrownBy(() -> EventContract.decode("no-json".getBytes(StandardCharsets.UTF_8))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publisherReturnsOnlyAfterAPositiveConfirm() {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(eq(EventContract.EXCHANGE), eq("order.approved"), any(Message.class), any(CorrelationData.class));
        new RabbitMqEventPublisher(rabbit, 1000).publish(event);
        verify(rabbit).send(eq(EventContract.EXCHANGE), eq("order.approved"), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void aNackOrABrokerFailureIsReportedSoTheOutboxKeepsTheEvent() {
        RabbitTemplate nacking = mock(RabbitTemplate.class);
        doAnswer(call -> {
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(false, "queue full"));
            return null;
        }).when(nacking).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertThatThrownBy(() -> new RabbitMqEventPublisher(nacking, 1000).publish(event)).isInstanceOf(EventPublishingException.class)
                .hasMessageContaining("queue full");
        RabbitTemplate silent = mock(RabbitTemplate.class); // no confirm ever arrives
        assertThatThrownBy(() -> new RabbitMqEventPublisher(silent, 100).publish(event)).isInstanceOf(EventPublishingException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisCacheServesHitsAndInvalidatesByVersion() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(RedisCatalogCache_VERSION())).thenReturn("3");
        when(values.get("hexaorders:catalog:v3:categories")).thenReturn(null, "[{\"id\":1,\"slug\":\"tecnologia\",\"name\":\"Tecnología\"}]");
        var registry = new SimpleMeterRegistry();
        var cache = new RedisCatalogCache(redis, Duration.ofSeconds(60), Duration.ofSeconds(30), registry);
        AtomicInteger loads = new AtomicInteger();
        List<Category> first = cache.categories(() -> { loads.incrementAndGet(); return List.of(new Category(1, "tecnologia", "Tecnología")); });
        List<Category> second = cache.categories(() -> { loads.incrementAndGet(); return List.of(); });
        assertThat(first).isEqualTo(second);
        assertThat(loads).hasValue(1);
        verify(values).set(eq("hexaorders:catalog:v3:categories"), anyString(), eq(Duration.ofSeconds(60)));
        cache.invalidate();
        verify(values).increment(RedisCatalogCache_VERSION());
        assertThat(registry.counter("hexaorders_cache_requests", "cache", "catalog", "result", "hit").count()).isEqualTo(1);
        assertThat(registry.counter("hexaorders_cache_requests", "cache", "catalog", "result", "miss").count()).isEqualTo(1);
    }

    private static String RedisCatalogCache_VERSION() { return "hexaorders:catalog:version"; }

    @Test
    void withoutRedisTheCatalogIsServedFromPostgresqlAndRedisIsSkippedForAWhile() {
        LettuceConnectionFactory unreachable = new LettuceConnectionFactory("127.0.0.1", 1); // nothing listens on port 1
        unreachable.afterPropertiesSet();
        try {
            var registry = new SimpleMeterRegistry();
            var cache = new RedisCatalogCache(new StringRedisTemplate(unreachable), Duration.ofSeconds(60), Duration.ofSeconds(30), registry);
            AtomicInteger loads = new AtomicInteger();
            long start = System.nanoTime();
            assertThat(cache.categories(() -> { loads.incrementAndGet(); return List.of(new Category(1, "a-b", "A")); })).hasSize(1);
            assertThat(cache.categories(() -> { loads.incrementAndGet(); return List.of(new Category(1, "a-b", "A")); })).hasSize(1);
            assertThat(loads).hasValue(2);
            assertThat(registry.counter("hexaorders_cache_requests", "cache", "catalog", "result", "error").count()).isEqualTo(1);
            assertThat(registry.counter("hexaorders_cache_requests", "cache", "catalog", "result", "bypass").count()).isEqualTo(1);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(10));
            cache.invalidate(); // must not throw while Redis is down
        } finally {
            unreachable.destroy();
        }
    }

    @Test
    void integrationsAreOffByDefaultAndFollowTheDeployment(@TempDir Path dir) throws Exception {
        var off = new MockEnvironment();
        new IntegrationEnvironment().postProcessEnvironment(off, new SpringApplication());
        assertThat(off.getProperty("hexaorders.integrations.redis.enabled")).isEqualTo("false");
        assertThat(off.getProperty("hexaorders.integrations.messaging.enabled")).isEqualTo("false");
        assertThat(off.getProperty("management.tracing.enabled")).isEqualTo("false");

        Path secret = Files.writeString(dir.resolve("rabbitmq_password"), "s3cret-from-file\n");
        var on = new MockEnvironment().withProperty("HEXAORDERS_INTEGRATIONS", "on").withProperty("RABBITMQ_PASSWORD_FILE", secret.toString())
                .withProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4318/");
        new IntegrationEnvironment().postProcessEnvironment(on, new SpringApplication());
        assertThat(on.getProperty("hexaorders.integrations.messaging.enabled")).isEqualTo("true");
        assertThat(on.getProperty("spring.rabbitmq.password")).isEqualTo("s3cret-from-file");
        assertThat(on.getProperty("management.otlp.tracing.endpoint")).isEqualTo("http://otel-collector:4318/v1/traces");

        var auto = new MockEnvironment().withProperty("HEXAORDERS_INTEGRATIONS", "auto").withProperty("REDIS_HOST", "localhost")
                .withProperty("RABBITMQ_HOST", "no-such-host.invalid");
        new IntegrationEnvironment().postProcessEnvironment(auto, new SpringApplication());
        assertThat(auto.getProperty("hexaorders.integrations.redis.enabled")).isEqualTo("true");
        assertThat(auto.getProperty("hexaorders.integrations.messaging.enabled")).isEqualTo("false");
    }
}
