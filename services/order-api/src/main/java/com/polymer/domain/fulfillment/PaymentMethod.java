package com.polymer.domain.fulfillment;

/**
 * Payment methods offered at checkout. v7: card, Yape and Plin are paid online right after placing
 * the order (card: instant; Yape/Plin: a QR / approval request valid for 60 seconds). The other three
 * are settled on delivery, by transfer or in store. Card numbers are never stored.
 */
public enum PaymentMethod {
    CARD("Tarjeta de crédito o débito", "Pago inmediato con Visa, Mastercard, American Express o Diners."),
    YAPE("Yape", "Escanea el QR o usa tu código de aprobación de Yape. La solicitud vence en 60 segundos."),
    PLIN("Plin", "Escanea el QR con la app de tu banco (Plin). La solicitud vence en 60 segundos."),
    CASH_ON_DELIVERY("Pago contra entrega", "Paga en efectivo o con tarjeta al recibir tu pedido."),
    BANK_TRANSFER("Transferencia bancaria", "Te enviamos los datos de la cuenta por correo; despachamos al confirmar el abono."),
    PAY_IN_STORE("Pago en tienda", "Paga al recoger tu pedido en tienda.");

    private final String label;
    private final String instructions;

    PaymentMethod(String label, String instructions) {
        this.label = label;
        this.instructions = instructions;
    }

    public String label() { return label; }
    public String instructions() { return instructions; }

    /** v7: se paga en línea justo después de confirmar el pedido. */
    public boolean online() { return this == CARD || this == YAPE || this == PLIN; }

    /** Cash on delivery needs a delivery; paying in store needs a store pickup. */
    public boolean allowedWith(DeliveryMethod delivery) {
        return switch (this) {
            case CASH_ON_DELIVERY -> delivery != DeliveryMethod.PICKUP;
            case PAY_IN_STORE -> delivery == DeliveryMethod.PICKUP;
            case BANK_TRANSFER, CARD, YAPE, PLIN -> true;
        };
    }

    public static PaymentMethod parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("paymentMethod: CARD, YAPE, PLIN, CASH_ON_DELIVERY, BANK_TRANSFER o PAY_IN_STORE");
        }
    }
}
