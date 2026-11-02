package com.hexaorders.application.service;

import com.hexaorders.application.port.out.MailSender.MailMessage;
import com.hexaorders.application.port.out.NotificationRecipients.Recipient;
import com.hexaorders.domain.event.BusinessEvent;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Text of the customer notifications (Spanish). Pure function of the event: easy to test. */
final class NotificationComposer {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private static final Map<String, String> STATUS = Map.ofEntries(
            Map.entry("CREATED", "Creado"), Map.entry("RISK_APPROVED", "Aprobado por riesgo"), Map.entry("RISK_REVIEW", "En revisión de riesgo"),
            Map.entry("CONFIRMED", "Confirmado"), Map.entry("PREPARING", "En preparación"), Map.entry("READY_FOR_DISPATCH", "Listo para despacho"),
            Map.entry("IN_TRANSIT", "En tránsito"), Map.entry("DELIVERED", "Entregado"), Map.entry("REJECTED", "Rechazado"),
            Map.entry("CANCELLED", "Cancelado"));

    private NotificationComposer() {}

    static MailMessage compose(BusinessEvent event, Recipient recipient) {
        String order = event.orderId().toString().substring(0, 8);
        String to = event.attributes().getOrDefault("to", "");
        String subject;
        String detail;
        switch (event.type()) {
            case ORDER_CREATED -> {
                subject = "Pedido " + order + " recibido";
                detail = "Hemos recibido tu pedido. El motor de riesgo lo está evaluando.";
            }
            case ORDER_APPROVED -> {
                subject = "Pedido " + order + " aprobado";
                detail = "risk-engine".equals(event.attributes().get("approvedBy"))
                        ? "Tu pedido superó la evaluación de riesgo automática."
                        : "El equipo de operaciones confirmó tu pedido.";
            }
            case ORDER_REJECTED -> {
                subject = "Pedido " + order + " rechazado";
                detail = "Tras la revisión, el pedido fue rechazado y las unidades volvieron al stock.";
            }
            case DELIVERY_SCHEDULED -> {
                subject = "Entrega " + ("RESCHEDULED".equals(event.attributes().get("action")) ? "reprogramada" : "programada")
                        + " para el pedido " + order;
                detail = "Ventana de entrega: " + WHEN.format(Instant.parse(event.attributes().get("windowStart")))
                        + " – " + WHEN.format(Instant.parse(event.attributes().get("windowEnd"))) + ".";
            }
            default -> {
                subject = "Pedido " + order + ": " + STATUS.getOrDefault(to, to);
                detail = "El estado de tu pedido cambió a «" + STATUS.getOrDefault(to, to) + "».";
            }
        }
        String body = "Hola, " + recipient.displayName() + ":\n\n" + detail + "\n\n"
                + "Pedido: " + event.orderId() + "\n"
                + "Fecha del evento: " + WHEN.format(event.occurredAt()) + "\n\n"
                + "Puedes seguirlo en HexaOrders: /cliente/seguimiento.html?id=" + event.orderId() + "\n\n"
                + "— HexaOrders (correo de demostración capturado por Mailpit; no se envía a Internet)\n"
                + "Referencia del evento: " + event.eventId() + "\n";
        return new MailMessage(recipient.email(), subject, body);
    }
}
