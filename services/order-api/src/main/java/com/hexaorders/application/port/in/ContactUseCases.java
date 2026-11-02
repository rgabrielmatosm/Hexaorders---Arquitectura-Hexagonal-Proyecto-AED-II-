package com.hexaorders.application.port.in;

import com.hexaorders.domain.contact.ContactMessage;
import java.util.List;

public interface ContactUseCases {
    /** Public: anyone can send a message from the contact page. */
    ContactMessage send(String name, String email, String topic, String message);
    /** ADMIN only. */
    List<ContactMessage> recent(AuthenticatedUser admin, int limit);
}
