package com.polymer.application.service;

import com.polymer.application.port.in.NotificationUseCases;
import com.polymer.application.port.out.IntegrationTelemetry;
import com.polymer.application.port.out.MailSender;
import com.polymer.application.port.out.NotificationRecipients;
import com.polymer.application.port.out.ProcessedEvents;
import com.polymer.application.port.out.ReceiptSource;
import com.polymer.domain.event.EventType;
import com.polymer.domain.event.BusinessEvent;
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
    private final ReceiptSource receipts;
    private final String publicUrl;
    private final boolean sandbox;

    public NotificationService(ProcessedEvents processed, NotificationRecipients recipients, MailSender mail,
                               IntegrationTelemetry telemetry) {
        this(processed, recipients, mail, telemetry, null, "", true);
    }

    /** v7: receipts para el correo de la boleta; publicUrl para los enlaces; sandbox = Mailpit. */
    public NotificationService(ProcessedEvents processed, NotificationRecipients recipients, MailSender mail,
                               IntegrationTelemetry telemetry, ReceiptSource receipts, String publicUrl, boolean sandbox) {
        this.processed = processed;
        this.recipients = recipients;
        this.mail = mail;
        this.telemetry = telemetry;
        this.receipts = receipts;
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
        this.sandbox = sandbox;
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
        if (event.type() == EventType.PAYMENT_CONFIRMED) {
            Optional<ReceiptSource.ReceiptMail> receipt = receipts == null ? Optional.empty() : receipts.forOrder(event.orderId());
            if (receipt.isEmpty()) {
                processed.markProcessed(event.eventId(), CONSUMER);
                telemetry.eventConsumed(event.type(), "skipped");
                return "skipped";
            }
            mail.send(NotificationComposer.receipt(event, recipient.get(), receipt.get(), publicUrl, sandbox));
        } else {
            mail.send(NotificationComposer.compose(event, recipient.get(), publicUrl, sandbox));
        }
        processed.markProcessed(event.eventId(), CONSUMER);
        telemetry.notificationSent(event.type());
        telemetry.eventConsumed(event.type(), "sent");
        return "sent";
    }
}
