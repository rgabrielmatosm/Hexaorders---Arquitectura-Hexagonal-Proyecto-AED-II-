package com.polymer.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Who must be notified about an order (the customer who placed it). Empty for lab orders. */
public interface NotificationRecipients {
    Optional<Recipient> forOrder(UUID orderId);

    record Recipient(String displayName, String email) {}
}
