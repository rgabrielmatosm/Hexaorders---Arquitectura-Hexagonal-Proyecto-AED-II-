package com.polymer.application.service;

import com.polymer.domain.identity.Language;
import java.util.Map;

/**
 * Textos de los correos al cliente en los idiomas de la tienda (capa de aplicación, sin frameworks).
 * Marcadores: {order} número corto, {status} estado, {window} ventana de entrega, {name} cliente.
 * Los estados y la marca POLYMER no cambian; los importes y fechas no se traducen.
 */
final class NotificationTexts {
    private NotificationTexts() {}

    static Map<String, String> of(Language language) {
        return switch (language) {
            case ES -> ES;
            case EN -> EN;
            case FR -> FR;
            case IT -> IT;
            case PT -> PT;
            case DE -> DE;
        };
    }

    private static final Map<String, String> ES = Map.ofEntries(
            Map.entry("created.subject", "Pedido {order} recibido"),
            Map.entry("created.detail", "¡Gracias por tu compra! Recibimos tu pedido y lo estamos verificando."),
            Map.entry("approved.subject", "Pedido {order} aprobado"),
            Map.entry("approved.byRisk", "Tu pedido fue verificado y pronto pasará a preparación."),
            Map.entry("approved.byStaff", "Nuestro equipo confirmó tu pedido y lo está preparando."),
            Map.entry("rejected.subject", "Pedido {order} rechazado"),
            Map.entry("rejected.detail", "No pudimos aprobar tu pedido tras la verificación. No se realizó ningún cobro; escríbenos si necesitas ayuda."),
            Map.entry("delivery.scheduled", "Entrega programada para el pedido {order}"),
            Map.entry("delivery.rescheduled", "Entrega reprogramada para el pedido {order}"),
            Map.entry("delivery.detail", "Ventana de entrega: {window}."),
            Map.entry("status.subject", "Pedido {order}: {status}"),
            Map.entry("status.detail", "El estado de tu pedido cambió a «{status}»."),
            Map.entry("greeting", "Hola, {name}:"),
            Map.entry("order", "Pedido"),
            Map.entry("eventDate", "Fecha del evento"),
            Map.entry("follow", "Puedes seguirlo en tu cuenta Polymer: {path}"),
            Map.entry("trackingPath", "/es/seguimiento?id="),
            Map.entry("signature", "— Equipo Polymer · Tecnología que conecta contigo."),
            Map.entry("sandbox", "(Entorno de pruebas: este correo lo captura Mailpit y no se envía a Internet.)"),
            Map.entry("reference", "Referencia del evento"),
            Map.entry("CREATED", "Pedido recibido"), Map.entry("RISK_APPROVED", "Pedido verificado"), Map.entry("RISK_REVIEW", "En verificación"),
            Map.entry("CONFIRMED", "Confirmado"), Map.entry("PREPARING", "En preparación"), Map.entry("READY_FOR_DISPATCH", "Listo para envío"),
            Map.entry("IN_TRANSIT", "En camino"), Map.entry("DELIVERED", "Entregado"), Map.entry("REJECTED", "No aprobado"),
            Map.entry("CANCELLED", "Cancelado"));

    private static final Map<String, String> EN = Map.ofEntries(
            Map.entry("created.subject", "Order {order} received"),
            Map.entry("created.detail", "Thank you for your purchase! We received your order and are verifying it."),
            Map.entry("approved.subject", "Order {order} approved"),
            Map.entry("approved.byRisk", "Your order has been verified and will soon move to preparation."),
            Map.entry("approved.byStaff", "Our team confirmed your order and is preparing it."),
            Map.entry("rejected.subject", "Order {order} declined"),
            Map.entry("rejected.detail", "We could not approve your order after verification. You have not been charged; write to us if you need help."),
            Map.entry("delivery.scheduled", "Delivery scheduled for order {order}"),
            Map.entry("delivery.rescheduled", "Delivery rescheduled for order {order}"),
            Map.entry("delivery.detail", "Delivery window: {window}."),
            Map.entry("status.subject", "Order {order}: {status}"),
            Map.entry("status.detail", "Your order status changed to “{status}”."),
            Map.entry("greeting", "Hello, {name}:"),
            Map.entry("order", "Order"),
            Map.entry("eventDate", "Event date"),
            Map.entry("follow", "Track it in your Polymer account: {path}"),
            Map.entry("trackingPath", "/en/tracking?id="),
            Map.entry("signature", "— The Polymer team · Technology that connects with you."),
            Map.entry("sandbox", "(Test environment: this email is captured by Mailpit and is not sent to the Internet.)"),
            Map.entry("reference", "Event reference"),
            Map.entry("CREATED", "Order received"), Map.entry("RISK_APPROVED", "Order verified"), Map.entry("RISK_REVIEW", "Under verification"),
            Map.entry("CONFIRMED", "Confirmed"), Map.entry("PREPARING", "Being prepared"), Map.entry("READY_FOR_DISPATCH", "Ready to ship"),
            Map.entry("IN_TRANSIT", "On its way"), Map.entry("DELIVERED", "Delivered"), Map.entry("REJECTED", "Not approved"),
            Map.entry("CANCELLED", "Cancelled"));

    private static final Map<String, String> FR = Map.ofEntries(
            Map.entry("created.subject", "Commande {order} reçue"),
            Map.entry("created.detail", "Merci pour votre achat ! Nous avons reçu votre commande et nous la vérifions."),
            Map.entry("approved.subject", "Commande {order} approuvée"),
            Map.entry("approved.byRisk", "Votre commande a été vérifiée et passera bientôt en préparation."),
            Map.entry("approved.byStaff", "Notre équipe a confirmé votre commande et la prépare."),
            Map.entry("rejected.subject", "Commande {order} refusée"),
            Map.entry("rejected.detail", "Nous n'avons pas pu approuver votre commande après vérification. Aucun paiement n'a été prélevé ; écrivez-nous si vous avez besoin d'aide."),
            Map.entry("delivery.scheduled", "Livraison programmée pour la commande {order}"),
            Map.entry("delivery.rescheduled", "Livraison reprogrammée pour la commande {order}"),
            Map.entry("delivery.detail", "Créneau de livraison : {window}."),
            Map.entry("status.subject", "Commande {order} : {status}"),
            Map.entry("status.detail", "Le statut de votre commande est passé à « {status} »."),
            Map.entry("greeting", "Bonjour {name},"),
            Map.entry("order", "Commande"),
            Map.entry("eventDate", "Date de l'événement"),
            Map.entry("follow", "Suivez-la depuis votre compte Polymer : {path}"),
            Map.entry("trackingPath", "/fr/suivi?id="),
            Map.entry("signature", "— L'équipe Polymer · La technologie qui vous relie."),
            Map.entry("sandbox", "(Environnement de test : cet e-mail est capturé par Mailpit et n'est pas envoyé sur Internet.)"),
            Map.entry("reference", "Référence de l'événement"),
            Map.entry("CREATED", "Commande reçue"), Map.entry("RISK_APPROVED", "Commande vérifiée"), Map.entry("RISK_REVIEW", "En vérification"),
            Map.entry("CONFIRMED", "Confirmée"), Map.entry("PREPARING", "En préparation"), Map.entry("READY_FOR_DISPATCH", "Prête à l'expédition"),
            Map.entry("IN_TRANSIT", "En route"), Map.entry("DELIVERED", "Livrée"), Map.entry("REJECTED", "Non approuvée"),
            Map.entry("CANCELLED", "Annulée"));

    private static final Map<String, String> IT = Map.ofEntries(
            Map.entry("created.subject", "Ordine {order} ricevuto"),
            Map.entry("created.detail", "Grazie per il tuo acquisto! Abbiamo ricevuto il tuo ordine e lo stiamo verificando."),
            Map.entry("approved.subject", "Ordine {order} approvato"),
            Map.entry("approved.byRisk", "Il tuo ordine è stato verificato e passerà presto alla preparazione."),
            Map.entry("approved.byStaff", "Il nostro team ha confermato il tuo ordine e lo sta preparando."),
            Map.entry("rejected.subject", "Ordine {order} rifiutato"),
            Map.entry("rejected.detail", "Non è stato possibile approvare il tuo ordine dopo la verifica. Non è stato effettuato alcun addebito; scrivici se hai bisogno di aiuto."),
            Map.entry("delivery.scheduled", "Consegna programmata per l'ordine {order}"),
            Map.entry("delivery.rescheduled", "Consegna riprogrammata per l'ordine {order}"),
            Map.entry("delivery.detail", "Fascia di consegna: {window}."),
            Map.entry("status.subject", "Ordine {order}: {status}"),
            Map.entry("status.detail", "Lo stato del tuo ordine è cambiato in «{status}»."),
            Map.entry("greeting", "Ciao {name},"),
            Map.entry("order", "Ordine"),
            Map.entry("eventDate", "Data dell'evento"),
            Map.entry("follow", "Seguilo dal tuo account Polymer: {path}"),
            Map.entry("trackingPath", "/it/tracciamento?id="),
            Map.entry("signature", "— Il team Polymer · La tecnologia che ti connette."),
            Map.entry("sandbox", "(Ambiente di prova: questa email viene catturata da Mailpit e non viene inviata su Internet.)"),
            Map.entry("reference", "Riferimento dell'evento"),
            Map.entry("CREATED", "Ordine ricevuto"), Map.entry("RISK_APPROVED", "Ordine verificato"), Map.entry("RISK_REVIEW", "In verifica"),
            Map.entry("CONFIRMED", "Confermato"), Map.entry("PREPARING", "In preparazione"), Map.entry("READY_FOR_DISPATCH", "Pronto per la spedizione"),
            Map.entry("IN_TRANSIT", "In consegna"), Map.entry("DELIVERED", "Consegnato"), Map.entry("REJECTED", "Non approvato"),
            Map.entry("CANCELLED", "Annullato"));

    private static final Map<String, String> PT = Map.ofEntries(
            Map.entry("created.subject", "Pedido {order} recebido"),
            Map.entry("created.detail", "Obrigado pela sua compra! Recebemos o seu pedido e estamos a verificá-lo."),
            Map.entry("approved.subject", "Pedido {order} aprovado"),
            Map.entry("approved.byRisk", "O seu pedido foi verificado e em breve passará para a preparação."),
            Map.entry("approved.byStaff", "A nossa equipa confirmou o seu pedido e está a prepará-lo."),
            Map.entry("rejected.subject", "Pedido {order} recusado"),
            Map.entry("rejected.detail", "Não foi possível aprovar o seu pedido após a verificação. Nenhuma cobrança foi feita; escreva-nos se precisar de ajuda."),
            Map.entry("delivery.scheduled", "Entrega agendada para o pedido {order}"),
            Map.entry("delivery.rescheduled", "Entrega reagendada para o pedido {order}"),
            Map.entry("delivery.detail", "Janela de entrega: {window}."),
            Map.entry("status.subject", "Pedido {order}: {status}"),
            Map.entry("status.detail", "O estado do seu pedido mudou para «{status}»."),
            Map.entry("greeting", "Olá, {name}:"),
            Map.entry("order", "Pedido"),
            Map.entry("eventDate", "Data do evento"),
            Map.entry("follow", "Acompanhe-o na sua conta Polymer: {path}"),
            Map.entry("trackingPath", "/pt/rastreamento?id="),
            Map.entry("signature", "— Equipa Polymer · Tecnologia que se liga a si."),
            Map.entry("sandbox", "(Ambiente de testes: este e-mail é capturado pelo Mailpit e não é enviado para a Internet.)"),
            Map.entry("reference", "Referência do evento"),
            Map.entry("CREATED", "Pedido recebido"), Map.entry("RISK_APPROVED", "Pedido verificado"), Map.entry("RISK_REVIEW", "Em verificação"),
            Map.entry("CONFIRMED", "Confirmado"), Map.entry("PREPARING", "Em preparação"), Map.entry("READY_FOR_DISPATCH", "Pronto para envio"),
            Map.entry("IN_TRANSIT", "A caminho"), Map.entry("DELIVERED", "Entregue"), Map.entry("REJECTED", "Não aprovado"),
            Map.entry("CANCELLED", "Cancelado"));

    private static final Map<String, String> DE = Map.ofEntries(
            Map.entry("created.subject", "Bestellung {order} eingegangen"),
            Map.entry("created.detail", "Vielen Dank für Ihren Einkauf! Wir haben Ihre Bestellung erhalten und prüfen sie."),
            Map.entry("approved.subject", "Bestellung {order} freigegeben"),
            Map.entry("approved.byRisk", "Ihre Bestellung wurde geprüft und geht bald in die Vorbereitung."),
            Map.entry("approved.byStaff", "Unser Team hat Ihre Bestellung bestätigt und bereitet sie vor."),
            Map.entry("rejected.subject", "Bestellung {order} abgelehnt"),
            Map.entry("rejected.detail", "Wir konnten Ihre Bestellung nach der Prüfung nicht freigeben. Es wurde nichts belastet; schreiben Sie uns, wenn Sie Hilfe brauchen."),
            Map.entry("delivery.scheduled", "Lieferung für Bestellung {order} geplant"),
            Map.entry("delivery.rescheduled", "Lieferung für Bestellung {order} neu geplant"),
            Map.entry("delivery.detail", "Lieferzeitfenster: {window}."),
            Map.entry("status.subject", "Bestellung {order}: {status}"),
            Map.entry("status.detail", "Der Status Ihrer Bestellung hat sich auf „{status}“ geändert."),
            Map.entry("greeting", "Hallo {name},"),
            Map.entry("order", "Bestellung"),
            Map.entry("eventDate", "Datum des Ereignisses"),
            Map.entry("follow", "Verfolgen Sie sie in Ihrem Polymer-Konto: {path}"),
            Map.entry("trackingPath", "/de/sendungsverfolgung?id="),
            Map.entry("signature", "— Ihr Polymer-Team · Technologie, die verbindet."),
            Map.entry("sandbox", "(Testumgebung: Diese E-Mail wird von Mailpit abgefangen und nicht ins Internet gesendet.)"),
            Map.entry("reference", "Ereignisreferenz"),
            Map.entry("CREATED", "Bestellung eingegangen"), Map.entry("RISK_APPROVED", "Bestellung geprüft"), Map.entry("RISK_REVIEW", "In Prüfung"),
            Map.entry("CONFIRMED", "Bestätigt"), Map.entry("PREPARING", "In Vorbereitung"), Map.entry("READY_FOR_DISPATCH", "Versandbereit"),
            Map.entry("IN_TRANSIT", "Unterwegs"), Map.entry("DELIVERED", "Zugestellt"), Map.entry("REJECTED", "Nicht freigegeben"),
            Map.entry("CANCELLED", "Storniert"));
}
