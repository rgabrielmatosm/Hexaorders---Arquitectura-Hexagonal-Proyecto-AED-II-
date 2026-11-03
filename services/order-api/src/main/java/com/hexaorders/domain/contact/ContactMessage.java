package com.hexaorders.domain.contact;

import java.time.Instant;

/** A message sent from the public contact page. Validated in the domain, persisted by an adapter. */
public record ContactMessage(long id, String name, String email, String topic, String message, Instant createdAt) {
    /** v6: ORDERS, WARRANTY and SALES are the store topics; DEMO and ACADEMIC remain valid for older messages. */
    public static final java.util.Set<String> TOPICS = java.util.Set.of("DEMO", "SUPPORT", "ACADEMIC", "OTHER", "ORDERS", "WARRANTY", "SALES");

    public ContactMessage {
        name = text(name, 2, 80, "name");
        email = text(email, 5, 254, "email");
        if (!email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) throw new IllegalArgumentException("email: formato no válido");
        if (topic == null || !TOPICS.contains(topic)) throw new IllegalArgumentException("topic: ORDERS, SUPPORT, WARRANTY, SALES u OTHER");
        message = text(message, 10, 2000, "message");
    }

    private static String text(String value, int min, int max, String field) {
        String clean = value == null ? "" : value.strip();
        if (clean.length() < min || clean.length() > max) {
            throw new IllegalArgumentException(field + ": entre " + min + " y " + max + " caracteres");
        }
        return clean;
    }
}
