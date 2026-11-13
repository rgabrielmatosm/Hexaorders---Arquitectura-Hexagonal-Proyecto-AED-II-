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
        // v7: correo. Mailpit en el perfil full; un relé SMTP real (p. ej. Gmail) si se configura SMTP_HOST y el secreto smtp_password.
        String smtpHost = env.getProperty("SMTP_HOST", "mailpit");
        props.put("polymer.integrations.mail.enabled", enabled(mode, smtpHost));
        props.put("polymer.mail.sandbox", "mailpit".equalsIgnoreCase(smtpHost.trim()));
        String smtpPassword = secret(env, "SMTP_PASSWORD_FILE");
        if (!smtpPassword.isEmpty()) {
            props.put("spring.mail.username", env.getProperty("SMTP_USERNAME", ""));
            props.put("spring.mail.password", smtpPassword);
            props.put("spring.mail.properties.mail.smtp.auth", true);
            props.put("spring.mail.properties.mail.smtp.starttls.enable", env.getProperty("SMTP_STARTTLS", "true"));
            props.put("spring.mail.properties.mail.smtp.starttls.required", env.getProperty("SMTP_STARTTLS", "true"));
        }
        // v7: «Continuar con Google». Sin client id/secret (archivos vacíos) el botón no se muestra.
        // v8: variables GOOGLE_CLIENT_ID/SECRET si se definen; si no, el JSON descargado de Google Cloud
        //     (secrets/google-oauth.json → GOOGLE_OAUTH_JSON_FILE); si no existe o está vacío, los archivos de la v7.
        String jsonFile = env.getProperty("GOOGLE_OAUTH_JSON_FILE", "");
        GoogleOAuthCredentials.Reading json = GoogleOAuthCredentials.read(jsonFile.isBlank() ? null : Path.of(jsonFile));
        String googleId;
        String googleSecret;
        boolean explicit = !env.getProperty("GOOGLE_CLIENT_ID", "").isBlank();  // p. ej. el proveedor de pruebas del CI
        if (json.present() && !explicit) {
            googleId = json.credentials().clientId();
            googleSecret = json.credentials().clientSecret();
            props.put("polymer.google.source", "secrets/google-oauth.json");
            props.put("polymer.google.redirect-uris", String.join(",", json.credentials().redirectUris()));
        } else {
            googleId = env.getProperty("GOOGLE_CLIENT_ID", "").isBlank() ? secret(env, "GOOGLE_CLIENT_ID_FILE") : env.getProperty("GOOGLE_CLIENT_ID").strip();
            googleSecret = env.getProperty("GOOGLE_CLIENT_SECRET", "").isBlank() ? secret(env, "GOOGLE_CLIENT_SECRET_FILE") : env.getProperty("GOOGLE_CLIENT_SECRET").strip();
            props.put("polymer.google.source", explicit ? "variables GOOGLE_CLIENT_ID y GOOGLE_CLIENT_SECRET"
                    : "secrets/google_client_id.txt y google_client_secret.txt");
            props.put("polymer.google.redirect-uris", "");
        }
        if (json.problem() != null) props.put("polymer.google.problem", json.problem());
        props.put("polymer.google.client-id", googleId);
        props.put("polymer.google.client-secret", googleSecret);
        env.getPropertySources().addFirst(new MapPropertySource("polymer-integrations", props));
    }

    /** Contenido de un secreto montado como archivo; vacío si la variable no existe o el archivo falta. */
    private static String secret(ConfigurableEnvironment env, String variable) {
        String file = env.getProperty(variable);
        if (file == null || file.isBlank() || !Files.isRegularFile(Path.of(file))) return "";
        try {
            return Files.readString(Path.of(file), StandardCharsets.UTF_8).strip();
        } catch (IOException unreadable) {
            throw new IllegalStateException("No se pudo leer " + file, unreadable);
        }
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
