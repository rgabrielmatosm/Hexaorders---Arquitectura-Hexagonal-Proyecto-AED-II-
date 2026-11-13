package com.polymer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.TestimonialDraft;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.TestimonialRepository;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.service.TestimonialService;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.testimonial.Testimonial;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** v9 · Testimonios: reglas del dominio y del caso de uso con un repositorio en memoria. */
class TestimonialServiceTest {
    private final List<Testimonial> rows = new ArrayList<>();
    private final TestimonialRepository repository = new TestimonialRepository() {
        public List<Testimonial> visible() { return rows.stream().filter(Testimonial::visible).sorted(Comparator.comparing((Testimonial t) -> t.kind() == Testimonial.Kind.TEAM ? 0 : 1)).toList(); }
        public List<Testimonial> all() { return List.copyOf(rows); }
        public Optional<Testimonial> find(long id) { return rows.stream().filter(t -> t.id() == id).findFirst(); }
        public Optional<Testimonial> forUser(long userId) { return rows.stream().filter(t -> t.userId() != null && t.userId() == userId).findFirst(); }
        public Testimonial save(Testimonial t) {
            Optional<Testimonial> existing = forUser(t.userId());
            long id = existing.map(Testimonial::id).orElse((long) rows.size() + 10);
            boolean visible = existing.map(Testimonial::visible).orElse(true);
            existing.ifPresent(rows::remove);
            Testimonial saved = new Testimonial(id, t.kind(), t.userId(), t.name(), t.role(), null, t.linkedinUrl(), t.comment(), t.rating(), visible, t.position(), t.createdAt());
            rows.add(saved);
            return saved;
        }
        public void setVisible(long id, boolean visible) {
            find(id).ifPresent(t -> { rows.remove(t); rows.add(t.withVisibility(visible)); });
        }
    };
    private final UserRepository users = mock(UserRepository.class);
    private final TestimonialService service = new TestimonialService(repository, users, mock(AuditLog.class),
            Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC));
    private final AuthenticatedUser customer = new AuthenticatedUser(7, "carla", Set.of(Role.CUSTOMER));
    private final AuthenticatedUser admin = new AuthenticatedUser(1, "admin", Set.of(Role.ADMIN));

    TestimonialServiceTest() {
        when(users.findById(7)).thenReturn(Optional.of(new UserAccount(7, "carla", "Carla Díaz", "carla@example.com",
                EnumSet.of(Role.CUSTOMER), true, Instant.EPOCH)));
        rows.add(new Testimonial(1, Testimonial.Kind.TEAM, null, "Integrante", "Equipo", "/img/equipo/a.jpg",
                "https://www.linkedin.com/in/integrante-123/", "Un comentario del equipo suficientemente largo.", 5, true, 1, Instant.EPOCH));
    }

    @Test
    void aCustomerLeavesOneTestimonialThatAppearsAfterTheTeamAndCanEditIt() {
        Testimonial saved = service.submit(customer, new TestimonialDraft("Compré mi laptop y llegó al día siguiente, excelente.", 5, "Diseñadora", null));
        assertThat(saved.name()).isEqualTo("Carla Díaz");      // el nombre sale de la cuenta, no del formulario
        assertThat(service.published()).extracting(Testimonial::kind).containsExactly(Testimonial.Kind.TEAM, Testimonial.Kind.CUSTOMER);
        service.submit(customer, new TestimonialDraft("Segunda opinión: el pago con Yape fue rapidísimo.", 4, null, null));
        assertThat(service.published()).hasSize(2);
        assertThat(service.mine(customer)).get().extracting(Testimonial::rating).isEqualTo(4);
    }

    @Test
    void theDomainRejectsShortTextsBadRatingsAndForeignLinks() {
        assertThatThrownBy(() -> service.submit(customer, new TestimonialDraft("Muy bueno", 5, null, null))).hasMessageContaining("al menos 20");
        assertThatThrownBy(() -> service.submit(customer, new TestimonialDraft("Un comentario con la longitud correcta.", 6, null, null))).hasMessageContaining("1 a 5");
        assertThatThrownBy(() -> service.submit(customer, new TestimonialDraft("Un comentario con la longitud correcta.", null, null, null))).hasMessageContaining("estrellas");
        assertThatThrownBy(() -> service.submit(customer, new TestimonialDraft("Un comentario con la longitud correcta.", 5, null, "https://evil.example/in/x")))
                .hasMessageContaining("linkedinUrl");
        assertThat(service.submit(customer, new TestimonialDraft("Un comentario con la longitud correcta.", 5, null,
                "https://www.linkedin.com/in/ronaldo-gabriel-matos-mart%C3%ADnez-614841392/")).linkedinUrl()).startsWith("https://www.linkedin.com/in/");
        assertThatThrownBy(() -> service.submit(null, new TestimonialDraft("Un comentario con la longitud correcta.", 5, null, null)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void onlyAdminsModerateAndHiddenOnesLeaveTheCarousel() {
        Testimonial saved = service.submit(customer, new TestimonialDraft("Atención muy amable y productos originales.", 5, null, null));
        assertThatThrownBy(() -> service.moderate(customer, saved.id(), false)).isInstanceOf(ForbiddenOperationException.class);
        service.moderate(admin, saved.id(), false);
        assertThat(service.published()).extracting(Testimonial::kind).containsExactly(Testimonial.Kind.TEAM);
        assertThat(service.all(admin)).hasSize(2);
        // Editar no lo vuelve a mostrar: la decisión de la administración se respeta.
        service.submit(customer, new TestimonialDraft("Lo vuelvo a intentar con otro texto distinto.", 5, null, null));
        assertThat(service.published()).hasSize(1);
    }
}
