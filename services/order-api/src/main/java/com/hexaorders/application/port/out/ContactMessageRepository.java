package com.hexaorders.application.port.out;

import com.hexaorders.domain.contact.ContactMessage;
import java.util.List;

public interface ContactMessageRepository {
    ContactMessage save(ContactMessage message);
    List<ContactMessage> recent(int limit);
}
