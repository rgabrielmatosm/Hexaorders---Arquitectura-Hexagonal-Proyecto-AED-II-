package com.polymer.adapter.out.payment;

import com.polymer.application.port.out.CardGateway;
import com.polymer.application.port.out.WalletGateway;
import com.polymer.domain.fulfillment.PaymentMethod;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * Yape y Plin de PRUEBAS. El QR codifica una solicitud de cobro simulada; la confirmación acepta un
 * celular peruano (9 dígitos que empiezan con 9) y un código de aprobación de 6 dígitos, salvo 000000,
 * que se rechaza para demostrar el error. En producción, un adaptador de la pasarela (p. ej. Niubiz o
 * Izipay con Yape) sustituye a este sin cambiar el núcleo.
 */
public final class SandboxWalletGateway implements WalletGateway {
    private final SecureRandom random = new SecureRandom();

    @Override
    public String qrPayload(PaymentMethod wallet, UUID paymentId, BigDecimal amount, String currency) {
        return "POLYMER-SANDBOX|" + wallet.name() + "|" + paymentId + "|" + amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + "|" + currency;
    }

    @Override
    public CardGateway.Result confirm(PaymentMethod wallet, UUID paymentId, String phone, String approvalCode, BigDecimal amount) {
        if ("000000".equals(approvalCode)) return CardGateway.Result.declined("WRONG_APPROVAL_CODE");
        return CardGateway.Result.approved((wallet == PaymentMethod.YAPE ? "YAPE-" : "PLIN-") + String.format("%08d", random.nextInt(100_000_000)));
    }
}
