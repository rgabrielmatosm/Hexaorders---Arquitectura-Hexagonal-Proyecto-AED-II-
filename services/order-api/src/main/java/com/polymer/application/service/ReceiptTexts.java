package com.polymer.application.service;

import com.polymer.domain.identity.Language;
import java.util.Map;

/** Textos del correo con la boleta de venta, en los idiomas de la tienda. */
final class ReceiptTexts {
    private ReceiptTexts() {}

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
            Map.entry("subject", "Boleta {receipt} · pago recibido del pedido {order}"),
            Map.entry("title", "Boleta de venta"),
            Map.entry("intro", "¡Pago recibido! Gracias por comprar en POLYMER. Esta es la boleta de tu compra."),
            Map.entry("product", "Producto"), Map.entry("qty", "Cant."), Map.entry("unit", "P. unit."), Map.entry("amount", "Importe"),
            Map.entry("subtotal", "Subtotal"), Map.entry("savings", "Ahorro en promociones"), Map.entry("shipping", "Envío"),
            Map.entry("free", "Gratis"), Map.entry("base", "Op. gravada"), Map.entry("igv", "IGV (18 %)"), Map.entry("total", "Total pagado"),
            Map.entry("paidWith", "Pagado con"), Map.entry("CARD", "Tarjeta"), Map.entry("operation", "N.º de operación"),
            Map.entry("issued", "Fecha de emisión"), Map.entry("order", "Pedido"), Map.entry("view", "Ver mi pedido y descargar la boleta"),
            Map.entry("disclaimer", "Comprobante interno de la tienda de demostración POLYMER (proyecto académico): no es un comprobante electrónico de SUNAT y no se realizó ningún cobro real."));

    private static final Map<String, String> EN = Map.ofEntries(
            Map.entry("subject", "Receipt {receipt} · payment received for order {order}"),
            Map.entry("title", "Sales receipt"),
            Map.entry("intro", "Payment received! Thank you for shopping at POLYMER. Here is the receipt for your purchase."),
            Map.entry("product", "Product"), Map.entry("qty", "Qty"), Map.entry("unit", "Unit price"), Map.entry("amount", "Amount"),
            Map.entry("subtotal", "Subtotal"), Map.entry("savings", "Promotion savings"), Map.entry("shipping", "Shipping"),
            Map.entry("free", "Free"), Map.entry("base", "Taxable amount"), Map.entry("igv", "IGV (18%)"), Map.entry("total", "Total paid"),
            Map.entry("paidWith", "Paid with"), Map.entry("CARD", "Card"), Map.entry("operation", "Transaction no."),
            Map.entry("issued", "Issue date"), Map.entry("order", "Order"), Map.entry("view", "View my order and download the receipt"),
            Map.entry("disclaimer", "Internal receipt of the POLYMER demo store (academic project): it is not a SUNAT electronic receipt and no real charge was made."));

    private static final Map<String, String> FR = Map.ofEntries(
            Map.entry("subject", "Reçu {receipt} · paiement reçu pour la commande {order}"),
            Map.entry("title", "Reçu de vente"),
            Map.entry("intro", "Paiement reçu ! Merci d'avoir acheté chez POLYMER. Voici le reçu de votre achat."),
            Map.entry("product", "Produit"), Map.entry("qty", "Qté"), Map.entry("unit", "Prix unit."), Map.entry("amount", "Montant"),
            Map.entry("subtotal", "Sous-total"), Map.entry("savings", "Économies promotions"), Map.entry("shipping", "Livraison"),
            Map.entry("free", "Gratuite"), Map.entry("base", "Montant HT"), Map.entry("igv", "IGV (18 %)"), Map.entry("total", "Total payé"),
            Map.entry("paidWith", "Payé avec"), Map.entry("CARD", "Carte"), Map.entry("operation", "N° d'opération"),
            Map.entry("issued", "Date d'émission"), Map.entry("order", "Commande"), Map.entry("view", "Voir ma commande et télécharger le reçu"),
            Map.entry("disclaimer", "Reçu interne de la boutique de démonstration POLYMER (projet académique) : ce n'est pas un justificatif électronique SUNAT et aucun débit réel n'a été effectué."));

    private static final Map<String, String> IT = Map.ofEntries(
            Map.entry("subject", "Ricevuta {receipt} · pagamento ricevuto per l'ordine {order}"),
            Map.entry("title", "Ricevuta di vendita"),
            Map.entry("intro", "Pagamento ricevuto! Grazie per aver acquistato su POLYMER. Ecco la ricevuta del tuo acquisto."),
            Map.entry("product", "Prodotto"), Map.entry("qty", "Qtà"), Map.entry("unit", "Prezzo unit."), Map.entry("amount", "Importo"),
            Map.entry("subtotal", "Subtotale"), Map.entry("savings", "Risparmio promozioni"), Map.entry("shipping", "Spedizione"),
            Map.entry("free", "Gratis"), Map.entry("base", "Imponibile"), Map.entry("igv", "IGV (18%)"), Map.entry("total", "Totale pagato"),
            Map.entry("paidWith", "Pagato con"), Map.entry("CARD", "Carta"), Map.entry("operation", "N. operazione"),
            Map.entry("issued", "Data di emissione"), Map.entry("order", "Ordine"), Map.entry("view", "Vedi il mio ordine e scarica la ricevuta"),
            Map.entry("disclaimer", "Ricevuta interna del negozio dimostrativo POLYMER (progetto accademico): non è un documento elettronico SUNAT e non è stato effettuato alcun addebito reale."));

    private static final Map<String, String> PT = Map.ofEntries(
            Map.entry("subject", "Recibo {receipt} · pagamento recebido do pedido {order}"),
            Map.entry("title", "Recibo de venda"),
            Map.entry("intro", "Pagamento recebido! Obrigado por comprar na POLYMER. Este é o recibo da sua compra."),
            Map.entry("product", "Produto"), Map.entry("qty", "Qtd."), Map.entry("unit", "Preço unit."), Map.entry("amount", "Valor"),
            Map.entry("subtotal", "Subtotal"), Map.entry("savings", "Economia em promoções"), Map.entry("shipping", "Frete"),
            Map.entry("free", "Grátis"), Map.entry("base", "Base tributável"), Map.entry("igv", "IGV (18%)"), Map.entry("total", "Total pago"),
            Map.entry("paidWith", "Pago com"), Map.entry("CARD", "Cartão"), Map.entry("operation", "N.º da operação"),
            Map.entry("issued", "Data de emissão"), Map.entry("order", "Pedido"), Map.entry("view", "Ver meu pedido e baixar o recibo"),
            Map.entry("disclaimer", "Recibo interno da loja de demonstração POLYMER (projeto acadêmico): não é um comprovante eletrônico da SUNAT e nenhuma cobrança real foi feita."));

    private static final Map<String, String> DE = Map.ofEntries(
            Map.entry("subject", "Beleg {receipt} · Zahlung für Bestellung {order} erhalten"),
            Map.entry("title", "Kaufbeleg"),
            Map.entry("intro", "Zahlung erhalten! Danke für deinen Einkauf bei POLYMER. Hier ist der Beleg zu deinem Kauf."),
            Map.entry("product", "Produkt"), Map.entry("qty", "Menge"), Map.entry("unit", "Einzelpreis"), Map.entry("amount", "Betrag"),
            Map.entry("subtotal", "Zwischensumme"), Map.entry("savings", "Ersparnis durch Aktionen"), Map.entry("shipping", "Versand"),
            Map.entry("free", "Kostenlos"), Map.entry("base", "Nettobetrag"), Map.entry("igv", "IGV (18 %)"), Map.entry("total", "Bezahlt"),
            Map.entry("paidWith", "Bezahlt mit"), Map.entry("CARD", "Karte"), Map.entry("operation", "Vorgangsnr."),
            Map.entry("issued", "Ausstellungsdatum"), Map.entry("order", "Bestellung"), Map.entry("view", "Bestellung ansehen und Beleg herunterladen"),
            Map.entry("disclaimer", "Interner Beleg des POLYMER-Demoshops (Studienprojekt): kein elektronischer SUNAT-Beleg, es wurde nichts tatsächlich abgebucht."));
}
