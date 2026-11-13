package com.polymer.application.service;

import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.TestimonialDraft;
import com.polymer.application.port.in.TestimonialUseCases;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.TestimonialRepository;
import com.polymer.application.port.out.UserRepository;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.testimonial.Testimonial;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** v9 · Caso de uso de los testimonios. Solo conoce sus puertos; la web y JDBC son adaptadores. */
public final class TestimonialService implements TestimonialUseCases {
    private final TestimonialRepository testimonials;
    private final UserRepository users;
    private final AuditLog audit;
    private final Clock clock;

    public TestimonialService(TestimonialRepository testimonials, UserRepository users, AuditLog audit, Clock clock) {
        this.testimonials = testimonials;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<Testimonial> published() { return testimonials.visible(); }

    @Override
    public Optional<Testimonial> mine(AuthenticatedUser user) {
        if (user == null) return Optional.empty();
        return testimonials.forUser(user.userId());
    }

    @Override
    public Testimonial submit(AuthenticatedUser user, TestimonialDraft draft) {
        if (user == null) throw new com.polymer.application.exception.ForbiddenOperationException("Inicia sesión para dejar tu testimonio");
        if (draft == null || draft.rating() == null) throw new IllegalArgumentException("rating: elige de 1 a 5 estrellas");
        UserAccount account = users.findById(user.userId()).orElseThrow(() -> new ResourceNotFoundException("Cuenta no encontrada"));
        Testimonial testimonial = Testimonial.fromCustomer(account.id(), account.displayName(), draft.role(), draft.comment(),
                draft.rating(), draft.linkedinUrl(), clock.instant().truncatedTo(ChronoUnit.MICROS));
        boolean edited = testimonials.forUser(account.id()).isPresent();
        Testimonial saved = testimonials.save(testimonial);
        audit.record("user:" + account.id(), edited ? "TESTIMONIAL_UPDATED" : "TESTIMONIAL_CREATED", "testimonial:" + saved.id(),
                saved.rating() + " estrellas");
        return saved;
    }

    @Override
    public List<Testimonial> all(AuthenticatedUser admin) {
        Guards.require(admin, Role.ADMIN);
        return testimonials.all();
    }

    @Override
    public Testimonial moderate(AuthenticatedUser admin, long id, boolean visible) {
        Guards.require(admin, Role.ADMIN);
        Testimonial current = testimonials.find(id).orElseThrow(() -> new ResourceNotFoundException("Testimonio no encontrado"));
        testimonials.setVisible(id, visible);
        audit.record("user:" + admin.userId(), visible ? "TESTIMONIAL_SHOWN" : "TESTIMONIAL_HIDDEN", "testimonial:" + id, current.kind().name());
        return current.withVisibility(visible);
    }
}
