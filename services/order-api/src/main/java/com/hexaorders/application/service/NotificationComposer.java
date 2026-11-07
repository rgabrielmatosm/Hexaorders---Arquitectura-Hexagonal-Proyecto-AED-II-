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
            Map.entry("CREATED", "Pedido recibido"), Map.entry("RISK_APPROVED", "Pedido verificado"), Map.entry("RISK_REVIEW", "En verificación"),
            Map.entry("CONFIRMED", "Confirmado"), Map.entry("PREPARING", "En preparación"), Map.entry("READY_FOR_DISPATCH", "Listo para envío"),
            Map.entry("IN_TRANSIT", "En camino"), Map.entry("DELIVERED", "Entregado"), Map.entry("REJECTED", "No aprobado"),
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
                detail = "¡Gracias por tu compra! Recibimos tu pedido y lo estamos verificando.";
            }
            case ORDER_APPROVED -> {
                subject = "Pedido " + order + " aprobado";
                detail = "risk-engine".equals(event.attributes().get("approvedBy"))
                        ? "Tu pedido fue verificado y pronto pasará a preparación."
                        : "Nuestro equipo confirmó tu pedido y lo está preparando.";
            }
            case ORDER_REJECTED -> {
                subject = "Pedido " + order + " rechazado";
                detail = "No pudimos aprobar tu pedido tras la verificación. No se realizó ningún cobro; escríbenos si necesitas ayuda.";
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
                + "Puedes seguirlo en tu cuenta Polymer: /cliente/seguimiento.html?id=" + event.orderId() + "\n\n"
                + "— Equipo Polymer · Tecnología que conecta contigo.\n\n"
                + "(Entorno de pruebas: este correo lo captura Mailpit y no se envía a Internet.)\n"
                + "Referencia del evento: " + event.eventId() + "\n";
        return new MailMessage(recipient.email(), subject, body);
    }
}
