package com.polymer.application.port.in;

import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.logistics.Delivery;
import com.polymer.domain.logistics.DeliveryChange;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface LogisticsUseCases {
    List<DeliveryView> calendar(AuthenticatedUser user, Instant from, Instant to, Long assigneeId);
    List<UserAccount> assignees(AuthenticatedUser user);
    List<CommerceOrder> schedulableOrders(AuthenticatedUser user);
    Delivery schedule(AuthenticatedUser user, UUID orderId, long assigneeId, Instant start, Instant end, String notes);
    Delivery reschedule(AuthenticatedUser user, long deliveryId, long assigneeId, Instant start, Instant end, String reason);
    List<DeliveryChange> history(AuthenticatedUser user, long deliveryId);
}
