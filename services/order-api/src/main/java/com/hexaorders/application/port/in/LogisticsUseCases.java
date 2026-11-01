package com.hexaorders.application.port.in;

import com.hexaorders.domain.fulfillment.CommerceOrder;
import com.hexaorders.domain.identity.UserAccount;
import com.hexaorders.domain.logistics.Delivery;
import com.hexaorders.domain.logistics.DeliveryChange;
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
