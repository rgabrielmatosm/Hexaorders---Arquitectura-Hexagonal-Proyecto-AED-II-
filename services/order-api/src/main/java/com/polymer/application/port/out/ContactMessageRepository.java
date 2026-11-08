package com.polymer.application.port.out;

import com.polymer.domain.contact.ContactMessage;
import java.util.List;

public interface ContactMessageRepository {
    ContactMessage save(ContactMessage message);
    List<ContactMessage> recent(int limit);
}
