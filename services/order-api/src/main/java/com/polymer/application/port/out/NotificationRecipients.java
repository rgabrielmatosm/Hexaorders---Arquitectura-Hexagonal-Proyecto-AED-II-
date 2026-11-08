package com.polymer.application.port.out;

import com.polymer.domain.identity.Language;
import java.util.Optional;
import java.util.UUID;

/** Who must be notified about an order (the customer who placed it). Empty for lab orders. */
public interface NotificationRecipients {
    Optional<Recipient> forOrder(UUID orderId);

    record Recipient(String displayName, String email, Language language) {
        public Recipient {
            language = language == null ? Language.DEFAULT : language;
        }

        public Recipient(String displayName, String email) { this(displayName, email, Language.DEFAULT); }
    }
}
