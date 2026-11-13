package com.polymer.domain.testimonial;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * v9 · Testimonio que aparece en el carrusel de la tienda.
 * TEAM: integrantes del equipo (foto y perfil de LinkedIn). CUSTOMER: un cliente con cuenta (uno por cuenta,
 * editable); la administración puede ocultarlo. Reglas puras de Java: sin Spring, JDBC ni HTTP.
 */
public record Testimonial(long id, Kind kind, Long userId, String name, String role, String photoUrl, String linkedinUrl,
                          String comment, Integer rating, boolean visible, int position, Instant createdAt) {
    public enum Kind { TEAM, CUSTOMER }

    public static final int MIN_COMMENT = 20;
    public static final int MAX_COMMENT = 600;
    public static final int MAX_NAME = 80;
    public static final int MAX_ROLE = 80;
    private static final Pattern LINKEDIN = Pattern.compile("^https://(www\\.)?linkedin\\.com/in/[A-Za-z0-9%_\\-]{3,100}/?$");

    public Testimonial {
        if (kind == null) throw new IllegalArgumentException("kind: obligatorio");
        name = clean(name, MAX_NAME, "name");
        if (name == null) throw new IllegalArgumentException("name: escribe tu nombre");
        role = clean(role, MAX_ROLE, "role");
        comment = clean(comment, MAX_COMMENT, "comment");
        if (comment == null || comment.length() < MIN_COMMENT) {
            throw new IllegalArgumentException("comment: cuéntanos tu experiencia en al menos " + MIN_COMMENT + " caracteres");
        }
        linkedinUrl = clean(linkedinUrl, 200, "linkedinUrl");
        if (linkedinUrl != null && !LINKEDIN.matcher(linkedinUrl).matches()) {
            throw new IllegalArgumentException("linkedinUrl: usa la dirección de tu perfil, por ejemplo https://www.linkedin.com/in/tu-nombre");
        }
        if (rating != null && (rating < 1 || rating > 5)) throw new IllegalArgumentException("rating: de 1 a 5 estrellas");
        if (kind == Kind.CUSTOMER && userId == null) throw new IllegalArgumentException("userId: un testimonio de cliente necesita su cuenta");
        if (kind == Kind.CUSTOMER && rating == null) throw new IllegalArgumentException("rating: elige de 1 a 5 estrellas");
    }

    /** Testimonio nuevo (o editado) de un cliente: visible al momento; la administración puede ocultarlo. */
    public static Testimonial fromCustomer(long userId, String name, String role, String comment, int rating, String linkedinUrl, Instant now) {
        return new Testimonial(0, Kind.CUSTOMER, userId, name, role, null, linkedinUrl, comment, rating, true, 100, now);
    }

    public Testimonial withVisibility(boolean show) {
        return new Testimonial(id, kind, userId, name, role, photoUrl, linkedinUrl, comment, rating, show, position, createdAt);
    }

    private static String clean(String value, int max, String field) {
        if (value == null) return null;
        String text = value.strip().replaceAll("\\s+", " ");
        if (text.isEmpty()) return null;
        if (text.length() > max) throw new IllegalArgumentException(field + ": máximo " + max + " caracteres");
        return text;
    }
}
