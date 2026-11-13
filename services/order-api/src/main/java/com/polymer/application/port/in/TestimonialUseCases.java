package com.polymer.application.port.in;

import com.polymer.domain.testimonial.Testimonial;
import java.util.List;
import java.util.Optional;

/** v9 · Testimonios del carrusel: equipo (con LinkedIn) y clientes con cuenta. */
public interface TestimonialUseCases {
    /** Público: los visibles, primero el equipo. */
    List<Testimonial> published();
    /** El testimonio de la persona autenticada, si ya dejó uno. */
    Optional<Testimonial> mine(AuthenticatedUser user);
    /** Crea o reemplaza el testimonio de la persona autenticada (uno por cuenta). */
    Testimonial submit(AuthenticatedUser user, TestimonialDraft draft);
    /** ADMIN: todos, incluidos los ocultos. */
    List<Testimonial> all(AuthenticatedUser admin);
    /** ADMIN: mostrar u ocultar uno. */
    Testimonial moderate(AuthenticatedUser admin, long id, boolean visible);
}
