package com.polymer.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Decides at startup which optional integrations of the "full" Docker profile are present.
 *
 * POLYMER_INTEGRATIONS = off (default outside Docker) | on | auto (set by docker-compose.yml).
 * In auto mode an integration is enabled when its Compose service name resolves in Docker's DNS,
 * i.e. when the container exists. The basic profile (no Redis, RabbitMQ, Mailpit or collector)
 * therefore runs quietly, and the full profile enables everything without manual switches.
 */
public final class IntegrationEnvironment implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        String mode = env.getProperty("POLYMER_INTEGRATIONS", "off").trim().toLowerCase();
        String redisHost = env.getProperty("REDIS_HOST", "redis");
        String rabbitHost = env.getProperty("RABBITMQ_HOST", "rabbitmq");
        String otlp = env.getProperty("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4318");
        boolean redis = enabled(mode, redisHost);
        boolean messaging = enabled(mode, rabbitHost);
        boolean tracing = enabled(mode, URI.create(otlp).getHost());
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("polymer.integrations.redis.enabled", redis);
        props.put("polymer.integrations.messaging.enabled", messaging);
        props.put("management.tracing.enabled", tracing);
        props.put("management.otlp.tracing.endpoint", otlp.replaceAll("/+$", "") + "/v1/traces");
        String passwordFile = env.getProperty("RABBITMQ_PASSWORD_FILE");
        if (messaging && passwordFile != null && Files.isRegularFile(Path.of(passwordFile))) {
            try {
                props.put("spring.rabbitmq.password", Files.readString(Path.of(passwordFile), StandardCharsets.UTF_8).strip());
            } catch (IOException unreadable) {
                throw new IllegalStateException("No se pudo leer " + passwordFile, unreadable);
            }
        }
        env.getPropertySources().addFirst(new MapPropertySource("polymer-integrations", props));
    }

    private static boolean enabled(String mode, String host) {
        return switch (mode) {
            case "on" -> true;
            case "auto" -> resolves(host);
            default -> false;
        };
    }

    private static boolean resolves(String host) {
        if (host == null || host.isBlank()) return false;
        try {
            InetAddress.getByName(host);
            return true;
        } catch (UnknownHostException absent) {
            return false;
        }
    }
}
