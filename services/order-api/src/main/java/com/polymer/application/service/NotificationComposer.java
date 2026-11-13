package com.polymer.application.service;

import com.polymer.application.port.out.MailSender.MailMessage;
import com.polymer.application.port.out.NotificationRecipients.Recipient;
import com.polymer.application.port.out.ReceiptSource.ReceiptMail;
import java.math.BigDecimal;
import java.time.ZoneId;
import com.polymer.domain.event.BusinessEvent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Text of the customer notifications, in the recipient's language. Pure function of the event: easy to test. */
final class NotificationComposer {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private NotificationComposer() {}

    static MailMessage compose(BusinessEvent event, Recipient recipient) { return compose(event, recipient, "", true); }

    /** publicUrl: origen público de la tienda («http://localhost:8080»); sandbox: el correo lo captura Mailpit. */
    static MailMessage compose(BusinessEvent event, Recipient recipient, String publicUrl, boolean sandbox) {
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
                + text.get("follow").replace("{path}", publicUrl + text.get("trackingPath") + event.orderId()) + "\n\n"
                + text.get("signature") + "\n\n"
                + (sandbox ? text.get("sandbox") + "\n" : "")
                + text.get("reference") + ": " + event.eventId() + "\n";
        return new MailMessage(recipient.email(), subject, body);
    }

    private static String fill(String template, String order, String status, String window) {
        return template.replace("{order}", order).replace("{status}", status).replace("{window}", window);
    }

    private static final DateTimeFormatter ISSUED = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Lima"));

    /** v7: correo con la boleta de un pago aprobado (texto y HTML), con enlace al pedido. */
    static MailMessage receipt(BusinessEvent event, Recipient recipient, ReceiptMail receipt, String publicUrl, boolean sandbox) {
        Map<String, String> r = ReceiptTexts.of(recipient.language());
        Map<String, String> n = NotificationTexts.of(recipient.language());
        String order = event.orderId().toString().substring(0, 8);
        String link = publicUrl + n.get("trackingPath") + event.orderId();
        String subject = r.get("subject").replace("{receipt}", receipt.code()).replace("{order}", order);
        String method = r.getOrDefault(receipt.method(), receipt.method())
                + (receipt.cardLast4() == null ? "" : " " + receipt.cardBrand() + " •••• " + receipt.cardLast4());
        StringBuilder plain = new StringBuilder(n.get("greeting").replace("{name}", recipient.displayName())).append("\n\n")
                .append(r.get("intro")).append("\n\n").append(r.get("title")).append(" ").append(receipt.code()).append("\n")
                .append(r.get("order")).append(": ").append(event.orderId()).append("\n")
                .append(r.get("issued")).append(": ").append(ISSUED.format(receipt.paidAt())).append("\n\n");
        StringBuilder rows = new StringBuilder();
        for (var line : receipt.lines()) {
            plain.append(line.quantity()).append(" × ").append(line.product()).append("  ").append(money(line.lineTotal(), receipt.currency())).append("\n");
            rows.append("<tr><td style=\"padding:8px 0;border-bottom:1px solid #eef1f6\">").append(MailLayout.escape(line.product())).append("</td>")
                .append("<td style=\"padding:8px;text-align:center;border-bottom:1px solid #eef1f6\">").append(line.quantity()).append("</td>")
                .append("<td style=\"padding:8px;text-align:right;border-bottom:1px solid #eef1f6;white-space:nowrap\">").append(money(line.unitPrice(), receipt.currency())).append("</td>")
                .append("<td style=\"padding:8px 0;text-align:right;border-bottom:1px solid #eef1f6;white-space:nowrap\">").append(money(line.lineTotal(), receipt.currency())).append("</td></tr>");
        }
        String shipping = receipt.shippingCost().signum() == 0 ? r.get("free") : money(receipt.shippingCost(), receipt.currency());
        plain.append("\n").append(r.get("subtotal")).append(": ").append(money(receipt.subtotal(), receipt.currency())).append("\n");
        if (receipt.savings().signum() > 0) plain.append(r.get("savings")).append(": -").append(money(receipt.savings(), receipt.currency())).append("\n");
        plain.append(r.get("shipping")).append(": ").append(shipping).append("\n")
                .append(r.get("base")).append(": ").append(money(receipt.taxableBase(), receipt.currency())).append("\n")
                .append(r.get("igv")).append(": ").append(money(receipt.igv(), receipt.currency())).append("\n")
                .append(r.get("total")).append(": ").append(money(receipt.total(), receipt.currency())).append("\n\n")
                .append(r.get("paidWith")).append(": ").append(method).append("\n")
                .append(r.get("operation")).append(": ").append(receipt.reference()).append("\n\n")
                .append(r.get("view")).append(": ").append(link).append("\n\n")
                .append(r.get("disclaimer")).append("\n\n").append(n.get("signature")).append("\n")
                .append(sandbox ? "\n" + n.get("sandbox") + "\n" : "");
        String totals = totalRow(r.get("subtotal"), money(receipt.subtotal(), receipt.currency()), false)
                + (receipt.savings().signum() > 0 ? totalRow(r.get("savings"), "-" + money(receipt.savings(), receipt.currency()), false) : "")
                + totalRow(r.get("shipping"), shipping, false)
                + totalRow(r.get("base"), money(receipt.taxableBase(), receipt.currency()), false)
                + totalRow(r.get("igv"), money(receipt.igv(), receipt.currency()), false)
                + totalRow(r.get("total"), money(receipt.total(), receipt.currency()), true);
        String html = MailLayout.page(subject,
                "<p>" + MailLayout.escape(n.get("greeting").replace("{name}", recipient.displayName())) + "</p>"
                + "<p>" + MailLayout.escape(r.get("intro")) + "</p>"
                + "<table role=\"presentation\" width=\"100%\" style=\"margin:18px 0;border:1px solid #e3e8f0;border-radius:12px;padding:14px 16px\"><tr>"
                + "<td><strong style=\"font-size:17px\">" + MailLayout.escape(r.get("title")) + "</strong><br><span style=\"font:600 15px ui-monospace,Consolas,monospace\">"
                + receipt.code() + "</span></td><td style=\"text-align:right;color:#5b6475;font-size:13px\">" + MailLayout.escape(r.get("issued")) + "<br>"
                + ISSUED.format(receipt.paidAt()) + "<br>" + MailLayout.escape(r.get("order")) + " " + order + "</td></tr></table>"
                + "<table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;font-size:14px\"><tr style=\"color:#5b6475;font-size:12px;text-transform:uppercase\">"
                + "<th align=\"left\" style=\"padding:6px 0\">" + MailLayout.escape(r.get("product")) + "</th><th style=\"padding:6px\">" + MailLayout.escape(r.get("qty"))
                + "</th><th align=\"right\" style=\"padding:6px\">" + MailLayout.escape(r.get("unit")) + "</th><th align=\"right\" style=\"padding:6px 0\">"
                + MailLayout.escape(r.get("amount")) + "</th></tr>" + rows + "</table>"
                + "<table role=\"presentation\" width=\"100%\" style=\"margin-top:12px;font-size:14px\">" + totals + "</table>"
                + "<p style=\"margin-top:16px\">" + MailLayout.escape(r.get("paidWith")) + ": <strong>" + MailLayout.escape(method) + "</strong><br>"
                + MailLayout.escape(r.get("operation")) + ": <span style=\"font-family:ui-monospace,Consolas,monospace\">" + MailLayout.escape(receipt.reference()) + "</span></p>"
                + "<p style=\"margin:22px 0\"><a href=\"" + MailLayout.escape(link) + "\" style=\"background:#2563eb;color:#ffffff;text-decoration:none;"
                + "padding:12px 20px;border-radius:10px;font-weight:600;display:inline-block\">" + MailLayout.escape(r.get("view")) + "</a></p>"
                + "<p style=\"color:#5b6475;font-size:12px\">" + MailLayout.escape(r.get("disclaimer")) + (sandbox ? " " + MailLayout.escape(n.get("sandbox")) : "") + "</p>",
                n.get("signature"));
        return new MailMessage(recipient.email(), subject, plain.toString(), html);
    }

    private static String totalRow(String label, String value, boolean strong) {
        String style = strong ? "font-weight:700;font-size:16px;border-top:2px solid #1d2433;padding-top:8px" : "color:#3b4252";
        return "<tr><td style=\"padding:3px 0;" + style + "\">" + MailLayout.escape(label) + "</td><td align=\"right\" style=\"padding:3px 0;white-space:nowrap;"
                + style + "\">" + MailLayout.escape(value) + "</td></tr>";
    }

    private static String money(BigDecimal amount, String currency) {
        return ("PEN".equals(currency) ? "S/ " : currency + " ") + amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
