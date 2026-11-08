package com.polymer.application.service;

import com.polymer.application.port.out.MailSender.MailMessage;
import com.polymer.application.port.out.NotificationRecipients.Recipient;
import com.polymer.domain.event.BusinessEvent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Text of the customer notifications, in the recipient's language. Pure function of the event: easy to test. */
final class NotificationComposer {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private NotificationComposer() {}

    static MailMessage compose(BusinessEvent event, Recipient recipient) {
        Map<String, String> text = NotificationTexts.of(recipient.language());
        String order = event.orderId().toString().substring(0, 8);
        String to = event.attributes().getOrDefault("to", "");
        String status = text.getOrDefault(to, to);
        String subject;
        String detail;
        switch (event.type()) {
            case ORDER_CREATED -> {
                subject = fill(text.get("created.subject"), order, status, "");
                detail = text.get("created.detail");
            }
            case ORDER_APPROVED -> {
                subject = fill(text.get("approved.subject"), order, status, "");
                detail = "risk-engine".equals(event.attributes().get("approvedBy")) ? text.get("approved.byRisk") : text.get("approved.byStaff");
            }
            case ORDER_REJECTED -> {
                subject = fill(text.get("rejected.subject"), order, status, "");
                detail = text.get("rejected.detail");
            }
            case DELIVERY_SCHEDULED -> {
                subject = fill(text.get("RESCHEDULED".equals(event.attributes().get("action")) ? "delivery.rescheduled" : "delivery.scheduled"),
                        order, status, "");
                String window = WHEN.format(Instant.parse(event.attributes().get("windowStart")))
                        + " – " + WHEN.format(Instant.parse(event.attributes().get("windowEnd")));
                detail = fill(text.get("delivery.detail"), order, status, window);
            }
            default -> {
                subject = fill(text.get("status.subject"), order, status, "");
                detail = fill(text.get("status.detail"), order, status, "");
            }
        }
        String body = text.get("greeting").replace("{name}", recipient.displayName()) + "\n\n" + detail + "\n\n"
                + text.get("order") + ": " + event.orderId() + "\n"
                + text.get("eventDate") + ": " + WHEN.format(event.occurredAt()) + "\n\n"
                + text.get("follow").replace("{path}", text.get("trackingPath") + event.orderId()) + "\n\n"
                + text.get("signature") + "\n\n"
                + text.get("sandbox") + "\n"
                + text.get("reference") + ": " + event.eventId() + "\n";
        return new MailMessage(recipient.email(), subject, body);
    }

    private static String fill(String template, String order, String status, String window) {
        return template.replace("{order}", order).replace("{status}", status).replace("{window}", window);
    }
}
