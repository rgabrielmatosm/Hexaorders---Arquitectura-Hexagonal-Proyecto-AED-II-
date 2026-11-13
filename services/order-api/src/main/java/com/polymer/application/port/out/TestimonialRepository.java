package com.polymer.application.port.out;

import com.polymer.domain.testimonial.Testimonial;
import java.util.List;
import java.util.Optional;

/** v9 · Persistencia de los testimonios (tabla testimonials, Flyway V13). */
public interface TestimonialRepository {
    List<Testimonial> visible();
    List<Testimonial> all();
    Optional<Testimonial> find(long id);
    Optional<Testimonial> forUser(long userId);
    /** Inserta, o reemplaza el del mismo usuario (uno por cuenta). */
    Testimonial save(Testimonial testimonial);
    void setVisible(long id, boolean visible);
}
