package com.hexaorders.domain.fulfillment;

/**
 * Payment methods offered at checkout. None of them collects card or bank data online: the store
 * records the customer's choice and the payment is settled on delivery, by transfer or in store.
 */
public enum PaymentMethod {
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

    /** Cash on delivery needs a delivery; paying in store needs a store pickup. */
    public boolean allowedWith(DeliveryMethod delivery) {
        return switch (this) {
            case CASH_ON_DELIVERY -> delivery != DeliveryMethod.PICKUP;
            case PAY_IN_STORE -> delivery == DeliveryMethod.PICKUP;
            case BANK_TRANSFER -> true;
        };
    }

    public static PaymentMethod parse(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return valueOf(value.strip().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("paymentMethod: CASH_ON_DELIVERY, BANK_TRANSFER o PAY_IN_STORE");
        }
    }
}
