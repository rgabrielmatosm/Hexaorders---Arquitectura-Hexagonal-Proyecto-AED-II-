package com.hexaorders.application.service;

import com.hexaorders.application.port.in.NotificationUseCases;
import com.hexaorders.application.port.out.IntegrationTelemetry;
import com.hexaorders.application.port.out.MailSender;
import com.hexaorders.application.port.out.NotificationRecipients;
import com.hexaorders.application.port.out.ProcessedEvents;
import com.hexaorders.domain.event.BusinessEvent;
import java.util.Optional;

/**
 * Idempotent consumer: an event already handled is ignored; the event is marked as processed only
 * after the e-mail was accepted by the SMTP server. If sending fails the exception propagates so
 * the messaging adapter retries and, after the retries, moves the message to the dead-letter queue.
 */
public final class NotificationService implements NotificationUseCases {
    static final String CONSUMER = "email-notifications";
    private final ProcessedEvents processed;
    private final NotificationRecipients recipients;
    private final MailSender mail;
    private final IntegrationTelemetry telemetry;

    public NotificationService(ProcessedEvents processed, NotificationRecipients recipients, MailSender mail,
                               IntegrationTelemetry telemetry) {
        this.processed = processed;
        this.recipients = recipients;
        this.mail = mail;
        this.telemetry = telemetry;
    }

    @Override
    public String handle(BusinessEvent event) {
        if (processed.alreadyProcessed(event.eventId(), CONSUMER)) {
            telemetry.eventConsumed(event.type(), "duplicate");
            return "duplicate";
        }
        Optional<NotificationRecipients.Recipient> recipient = recipients.forOrder(event.orderId());
        if (recipient.isEmpty()) {
            processed.markProcessed(event.eventId(), CONSUMER);
            telemetry.eventConsumed(event.type(), "skipped");
            return "skipped";
        }
        mail.send(NotificationComposer.compose(event, recipient.get()));
        processed.markProcessed(event.eventId(), CONSUMER);
        telemetry.notificationSent(event.type());
        telemetry.eventConsumed(event.type(), "sent");
        return "sent";
    }
}
