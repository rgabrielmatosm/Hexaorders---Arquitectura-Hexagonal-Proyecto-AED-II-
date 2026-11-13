package com.polymer.adapter.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.polymer.domain.event.BusinessEvent;
import com.polymer.domain.event.EventType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Wire contract of the integration events (shared by the publishing and the consuming adapter).
 * JSON, UTF-8, schemaVersion 1. Documented in docs/ASYNCHRONOUS_EVENTS.md.
 */
public final class EventContract {
    // Identificadores heredados a propósito (docs/MIGRACION_POLYMER.md): el exchange y las colas viven
    // en el volumen de RabbitMQ con sus mensajes pendientes; renombrarlos los dejaría huérfanos.
    public static final String EXCHANGE = "hexaorders.events";
    public static final String DEAD_LETTER_EXCHANGE = "hexaorders.events.dlx";
    public static final String NOTIFICATIONS_QUEUE = "hexaorders.notifications";
    public static final String NOTIFICATIONS_DLQ = "hexaorders.notifications.dlq";
    public static final String DEAD_LETTER_ROUTING_KEY = "notifications.dead";
    public static final String CONTENT_TYPE = "application/json";

    private static final ObjectMapper JSON = new ObjectMapper();

    private EventContract() {}

    public static byte[] encode(BusinessEvent event) {
        ObjectNode node = JSON.createObjectNode();
        node.put("eventId", event.eventId());
        node.put("type", event.type().name());
        node.put("routingKey", event.type().routingKey());
        node.put("schemaVersion", event.schemaVersion());
        node.put("occurredAt", event.occurredAt().toString());
        node.put("orderId", event.orderId().toString());
        ObjectNode attributes = node.putObject("attributes");
        event.attributes().forEach(attributes::put);
        try {
            return JSON.writeValueAsBytes(node);
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    /** @throws IllegalArgumentException for malformed messages or unsupported schema versions. */
    public static BusinessEvent decode(byte[] body) {
        try {
            JsonNode node = JSON.readTree(new String(body, StandardCharsets.UTF_8));
            int version = node.path("schemaVersion").asInt(0);
            if (version != BusinessEvent.SCHEMA_VERSION) throw new IllegalArgumentException("schemaVersion no soportada: " + version);
            Map<String, String> attributes = new LinkedHashMap<>();
            node.path("attributes").fields().forEachRemaining(entry -> attributes.put(entry.getKey(), entry.getValue().asText()));
            return new BusinessEvent(node.path("eventId").asText(), EventType.valueOf(node.path("type").asText()), version,
                    Instant.parse(node.path("occurredAt").asText()), UUID.fromString(node.path("orderId").asText()), attributes);
        } catch (IOException | RuntimeException malformed) {
            throw new IllegalArgumentException("Mensaje de evento no válido: " + malformed.getMessage(), malformed);
        }
    }
}
