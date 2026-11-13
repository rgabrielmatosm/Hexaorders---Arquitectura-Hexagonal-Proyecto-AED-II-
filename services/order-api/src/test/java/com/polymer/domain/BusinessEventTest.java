package com.polymer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polymer.domain.event.BusinessEvent;
import com.polymer.domain.event.EventType;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessEventTest {
    private static final UUID ORDER = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final Instant AT = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    void lifecycleChangesMapToTheIntegrationEventContract() {
        assertThat(BusinessEvent.fromStatusChange(1, ORDER, null, FulfillmentStatus.CREATED, AT).type()).isEqualTo(EventType.ORDER_CREATED);
        var byRisk = BusinessEvent.fromStatusChange(2, ORDER, FulfillmentStatus.CREATED, FulfillmentStatus.RISK_APPROVED, AT);
        assertThat(byRisk.type()).isEqualTo(EventType.ORDER_APPROVED);
        assertThat(byRisk.attributes()).containsEntry("approvedBy", "risk-engine").containsEntry("from", "CREATED").containsEntry("to", "RISK_APPROVED");
        assertThat(BusinessEvent.fromStatusChange(3, ORDER, FulfillmentStatus.RISK_REVIEW, FulfillmentStatus.CONFIRMED, AT).attributes())
                .containsEntry("approvedBy", "operations");
        assertThat(BusinessEvent.fromStatusChange(4, ORDER, FulfillmentStatus.RISK_REVIEW, FulfillmentStatus.REJECTED, AT).type()).isEqualTo(EventType.ORDER_REJECTED);
        assertThat(BusinessEvent.fromStatusChange(5, ORDER, FulfillmentStatus.READY_FOR_DISPATCH, FulfillmentStatus.IN_TRANSIT, AT).type())
                .isEqualTo(EventType.ORDER_STATUS_CHANGED);
    }

    @Test
    void eventIdsAreDeterministicSoDuplicatesCanBeDetected() {
        var first = BusinessEvent.fromStatusChange(42, ORDER, FulfillmentStatus.CONFIRMED, FulfillmentStatus.PREPARING, AT);
        var again = BusinessEvent.fromStatusChange(42, ORDER, FulfillmentStatus.CONFIRMED, FulfillmentStatus.PREPARING, AT);
        assertThat(first).isEqualTo(again);
        assertThat(first.eventId()).isEqualTo("order-status-42");
        assertThat(first.schemaVersion()).isEqualTo(BusinessEvent.SCHEMA_VERSION);
        assertThat(EventType.DELIVERY_SCHEDULED.routingKey()).isEqualTo("delivery.scheduled");
    }

    @Test
    void deliveryEventsCarryOnlyTheWindowAndNoPersonalData() {
        var event = BusinessEvent.fromDeliveryChange(7, ORDER, "CREATED", AT, AT.plusSeconds(7200), AT);
        assertThat(event.eventId()).isEqualTo("delivery-change-7");
        assertThat(event.attributes()).containsOnlyKeys("action", "windowStart", "windowEnd");
        assertThatThrownBy(() -> BusinessEvent.fromDeliveryChange(8, ORDER, "DELETED", AT, AT, AT)).hasMessageContaining("action");
        assertThatThrownBy(() -> new BusinessEvent(" ", EventType.ORDER_CREATED, 1, AT, ORDER, null)).hasMessageContaining("eventId");
    }
}
