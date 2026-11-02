package com.hexaorders.adapter.in.web;

import com.hexaorders.application.port.in.ContactUseCases;
import com.hexaorders.domain.contact.ContactMessage;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public contact page (CSRF-protected like every other form). */
@RestController
@RequestMapping("/api/contact")
public final class ContactController {
    private final ContactUseCases contact;

    public ContactController(ContactUseCases contact) { this.contact = contact; }

    @PostMapping
    public ResponseEntity<Receipt> send(@RequestBody ContactRequest request) {
        ContactMessage saved = contact.send(request.name(), request.email(), request.topic(), request.message());
        return ResponseEntity.created(URI.create("/api/contact/" + saved.id())).body(new Receipt(saved.id(), saved.createdAt().toString()));
    }

    public record ContactRequest(String name, String email, String topic, String message) {}
    /** The response never echoes the personal data back. */
    public record Receipt(long id, String receivedAt) {}
}
