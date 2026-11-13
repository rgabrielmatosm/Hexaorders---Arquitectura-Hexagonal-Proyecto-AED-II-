package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.TestimonialDraft;
import com.polymer.application.port.in.TestimonialUseCases;
import com.polymer.domain.testimonial.Testimonial;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** v9 · Adaptador de entrada de los testimonios: carrusel público, formulario del cliente y moderación (ADMIN). */
@RestController
public final class TestimonialController {
    private final TestimonialUseCases testimonials;

    public TestimonialController(TestimonialUseCases testimonials) { this.testimonials = testimonials; }

    @GetMapping("/api/testimonials")
    public ResponseEntity<List<View>> published() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(30, TimeUnit.SECONDS).cachePublic())
                .body(testimonials.published().stream().map(View::of).toList());
    }

    @GetMapping("/api/testimonials/mine")
    public ResponseEntity<View> mine() {
        return testimonials.mine(CurrentUser.require()).map(View::of).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/api/testimonials/mine")
    public View submit(@RequestBody TestimonialDraft draft) {
        return View.of(testimonials.submit(CurrentUser.require(), draft));
    }

    @GetMapping("/api/admin/testimonials")
    public List<AdminView> all() {
        return testimonials.all(CurrentUser.require()).stream().map(AdminView::of).toList();
    }

    @PutMapping("/api/admin/testimonials/{id}/visibility")
    public AdminView moderate(@PathVariable long id, @RequestBody Visibility body) {
        return AdminView.of(testimonials.moderate(CurrentUser.require(), id, body != null && body.visible()));
    }

    public record Visibility(boolean visible) {}

    /** Vista pública: nunca expone el id de la cuenta del cliente. */
    public record View(long id, String kind, String name, String role, String photoUrl, String linkedinUrl, String comment,
                       Integer rating, String createdAt) {
        static View of(Testimonial t) {
            return new View(t.id(), t.kind().name(), t.name(), t.role(), t.photoUrl(), t.linkedinUrl(), t.comment(), t.rating(),
                    t.createdAt() == null ? null : t.createdAt().toString());
        }
    }

    public record AdminView(long id, String kind, String name, String role, String comment, Integer rating, boolean visible, String createdAt) {
        static AdminView of(Testimonial t) {
            return new AdminView(t.id(), t.kind().name(), t.name(), t.role(), t.comment(), t.rating(), t.visible(),
                    t.createdAt() == null ? null : t.createdAt().toString());
        }
    }
}
