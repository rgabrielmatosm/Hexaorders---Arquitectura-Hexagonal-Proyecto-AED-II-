package com.polymer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymer.application.exception.EventPublishingException;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.CatalogCache;
import com.polymer.application.port.out.CatalogRepository;
import com.polymer.application.port.out.EventOutbox;
import com.polymer.application.port.out.EventPublisher;
import com.polymer.application.port.out.IntegrationTelemetry;
import com.polymer.application.port.out.MailSender;
import com.polymer.application.port.out.NotificationRecipients;
import com.polymer.application.port.out.ProcessedEvents;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.service.CatalogService;
import com.polymer.application.service.EventRelayService;
import com.polymer.application.service.NotificationService;
import com.polymer.domain.catalog.Category;
import com.polymer.domain.catalog.Product;
import com.polymer.domain.catalog.ProductDetails;
import com.polymer.domain.event.BusinessEvent;
import com.polymer.domain.event.EventType;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.identity.Role;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IntegrationServicesTest {
    private static final UUID ORDER = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
    private static final Instant AT = Instant.parse("2026-11-02T10:00:00Z");
    private final BusinessEvent created = BusinessEvent.fromStatusChange(10, ORDER, null, FulfillmentStatus.CREATED, AT);
    private final BusinessEvent approved = BusinessEvent.fromStatusChange(11, ORDER, FulfillmentStatus.CREATED, FulfillmentStatus.RISK_APPROVED, AT);

    @Test
    void relayMarksAnEventAsPublishedOnlyAfterTheBrokerConfirmedIt() {
        EventOutbox outbox = mock(EventOutbox.class);
        EventPublisher publisher = mock(EventPublisher.class);
        when(outbox.pending(anyInt())).thenReturn(List.of(created, approved));
        int published = new EventRelayService(outbox, publisher, IntegrationTelemetry.NONE).relayPending();
        assertThat(published).isEqualTo(2);
        var order = inOrder(publisher, outbox);
        order.verify(publisher).publish(created);
        order.verify(outbox).markPublished("order-status-10");
        order.verify(publisher).publish(approved);
        order.verify(outbox).markPublished("order-status-11");
    }

    @Test
    void whenTheBrokerIsDownEventsStayInTheOutboxForTheNextRun() {
        EventOutbox outbox = mock(EventOutbox.class);
        EventPublisher publisher = mock(EventPublisher.class);
        IntegrationTelemetry telemetry = mock(IntegrationTelemetry.class);
        when(outbox.pending(anyInt())).thenReturn(List.of(created, approved));
        doThrow(new EventPublishingException("down", null)).when(publisher).publish(created);
        assertThat(new EventRelayService(outbox, publisher, telemetry).relayPending()).isZero();
        verify(outbox, never()).markPublished(anyString());
        verify(publisher, never()).publish(approved); // order is preserved: nothing jumps the queue
        verify(telemetry).eventPublishFailed();
    }

    @Test
    void notificationsAreSentOnceAndDuplicatesAreIgnored() {
        ProcessedEvents processed = mock(ProcessedEvents.class);
        NotificationRecipients recipients = mock(NotificationRecipients.class);
        MailSender mail = mock(MailSender.class);
        when(recipients.forOrder(ORDER)).thenReturn(Optional.of(new NotificationRecipients.Recipient("Cliente", "cliente@polymer.local")));
        var service = new NotificationService(processed, recipients, mail, IntegrationTelemetry.NONE);
        assertThat(service.handle(approved)).isEqualTo("sent");
        var message = ArgumentCaptor.forClass(MailSender.MailMessage.class);
        var order = inOrder(mail, processed);
        order.verify(mail).send(message.capture());
        order.verify(processed).markProcessed("order-status-11", "email-notifications");
        assertThat(message.getValue().to()).isEqualTo("cliente@polymer.local");
        assertThat(message.getValue().subject()).contains("aaaaaaaa").contains("aprobado");
        assertThat(message.getValue().body()).contains("verificado").contains("Mailpit")
                .contains("Equipo Polymer").doesNotContain("POLYMER"); // POLYMER: marca comercial en los correos

        when(processed.alreadyProcessed("order-status-11", "email-notifications")).thenReturn(true);
        assertThat(service.handle(approved)).isEqualTo("duplicate");
        verify(mail, times(1)).send(any());
    }

    @Test
    void aFailedEmailIsNotMarkedAsProcessedSoTheMessageIsRetried() {
        ProcessedEvents processed = mock(ProcessedEvents.class);
        NotificationRecipients recipients = mock(NotificationRecipients.class);
        MailSender mail = mock(MailSender.class);
        when(recipients.forOrder(ORDER)).thenReturn(Optional.of(new NotificationRecipients.Recipient("Cliente", "cliente@polymer.local")));
        doThrow(new IllegalStateException("smtp down")).when(mail).send(any());
        var service = new NotificationService(processed, recipients, mail, IntegrationTelemetry.NONE);
        assertThatThrownBy(() -> service.handle(created)).hasMessage("smtp down");
        verify(processed, never()).markProcessed(anyString(), anyString());
    }

    @Test
    void ordersWithoutCustomerAreSkippedWithoutSendingMail() {
        ProcessedEvents processed = mock(ProcessedEvents.class);
        MailSender mail = mock(MailSender.class);
        NotificationRecipients none = orderId -> Optional.empty();
        var service = new NotificationService(processed, none, mail, IntegrationTelemetry.NONE);
        assertThat(service.handle(BusinessEvent.fromDeliveryChange(3, ORDER, "RESCHEDULED", AT, AT.plusSeconds(3600), AT))).isEqualTo("skipped");
        verify(mail, never()).send(any());
        verify(processed).markProcessed(eq("delivery-change-3"), anyString());
    }

    /** In-memory cache that records what the use case asks for. */
    static final class RecordingCache implements CatalogCache {
        final List<String> calls = new ArrayList<>();
        Object stored;
        @Override public List<Category> categories(Supplier<List<Category>> loader) { calls.add("categories"); return loader.get(); }
        @SuppressWarnings("unchecked")
        @Override public List<Product> activeProducts(Long categoryId, Supplier<List<Product>> loader) {
            calls.add("products:" + categoryId);
            if (stored == null) stored = loader.get();
            return (List<Product>) stored;
        }
        @Override public Optional<Product> product(long id, Supplier<Optional<Product>> loader) { calls.add("product:" + id); return loader.get(); }
        @Override public void invalidate() { calls.add("invalidate"); stored = null; }
    }

    @Test
    void publicCatalogReadsUseTheCacheButSearchesAndAdminChangesDoNot() {
        CatalogRepository repository = mock(CatalogRepository.class);
        var product = new Product(1, new ProductDetails("TEC-LAPTOP", "Portátil", "", 1, new BigDecimal("10.00"), true, 260), "Tecnología", 5);
        when(repository.products(null, 1L, true)).thenReturn(List.of(product));
        when(repository.products("lap", null, true)).thenReturn(List.of(product));
        when(repository.product(1)).thenReturn(Optional.of(product));
        when(repository.adjustStock(1, 3)).thenReturn(Optional.of(product));
        var cache = new RecordingCache();
        var service = new CatalogService(repository, mock(AuditLog.class), cache);

        service.products(null, 1L);
        service.products(null, 1L);
        verify(repository, times(1)).products(null, 1L, true); // second read served by the cache
        service.products("lap", null);
        assertThat(cache.calls).doesNotContain("products:null");

        service.adjustStock(new AuthenticatedUser(1, "admin", EnumSet.of(Role.ADMIN)), 1, 3, "reposición");
        assertThat(cache.calls).contains("invalidate"); // admin reads PostgreSQL directly, then invalidates
        service.products(null, 1L);
        verify(repository, times(2)).products(null, 1L, true); // after invalidation PostgreSQL answers again
    }

    private static int anyInt() { return org.mockito.ArgumentMatchers.anyInt(); }

    @Test
    void everyEventTypeHasACustomerFacingSubject() {
        ProcessedEvents processed = mock(ProcessedEvents.class);
        MailSender mail = mock(MailSender.class);
        NotificationRecipients recipient = orderId -> Optional.of(new NotificationRecipients.Recipient("Cliente", "c@polymer.local"));
        var service = new NotificationService(processed, recipient, mail, IntegrationTelemetry.NONE);
        for (FulfillmentStatus to : FulfillmentStatus.values()) {
            if (to == FulfillmentStatus.CREATED) continue;
            service.handle(BusinessEvent.fromStatusChange(100 + to.ordinal(), ORDER, FulfillmentStatus.CREATED, to, AT));
        }
        service.handle(BusinessEvent.fromDeliveryChange(9, ORDER, "CREATED", AT, AT.plusSeconds(3600), AT));
        var messages = ArgumentCaptor.forClass(MailSender.MailMessage.class);
        verify(mail, times(FulfillmentStatus.values().length)).send(messages.capture());
        assertThat(messages.getAllValues()).allSatisfy(m -> assertThat(m.subject()).startsWith("").isNotBlank().doesNotContain("null"));
        assertThat(messages.getAllValues()).anySatisfy(m -> assertThat(m.subject()).contains("rechazado"));
        assertThat(messages.getAllValues()).anySatisfy(m -> assertThat(m.subject()).contains("Entrega programada"));
        assertThat(EventType.values()).hasSize(5);
    }
}
