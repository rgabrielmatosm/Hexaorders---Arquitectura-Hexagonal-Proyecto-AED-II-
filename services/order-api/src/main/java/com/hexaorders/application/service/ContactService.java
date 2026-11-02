package com.hexaorders.application.service;

import com.hexaorders.application.port.in.AuthenticatedUser;
import com.hexaorders.application.port.in.ContactUseCases;
import com.hexaorders.application.port.out.AuditLog;
import com.hexaorders.application.port.out.ContactMessageRepository;
import com.hexaorders.domain.contact.ContactMessage;
import com.hexaorders.domain.identity.Role;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class ContactService implements ContactUseCases {
    private final ContactMessageRepository messages;
    private final AuditLog audit;
    private final Clock clock;

    public ContactService(ContactMessageRepository messages, AuditLog audit, Clock clock) {
        this.messages = messages;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public ContactMessage send(String name, String email, String topic, String message) {
        ContactMessage draft = new ContactMessage(0, name, email, topic == null ? null : topic.strip().toUpperCase(java.util.Locale.ROOT),
                message, clock.instant().truncatedTo(ChronoUnit.MICROS));
        ContactMessage saved = messages.save(draft);
        // The audit trail records that a message arrived, never its content or the sender's e-mail.
        audit.record("public", "CONTACT_MESSAGE_RECEIVED", "contact:" + saved.id(), "tema " + saved.topic());
        return saved;
    }

    @Override
    public List<ContactMessage> recent(AuthenticatedUser admin, int limit) {
        Guards.require(admin, Role.ADMIN);
        return messages.recent(Math.max(1, Math.min(limit, 200)));
    }
}
