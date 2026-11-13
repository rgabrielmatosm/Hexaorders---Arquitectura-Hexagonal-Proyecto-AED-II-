package com.polymer.adapter.out.payment;

import com.polymer.application.port.out.CardGateway;
import com.polymer.domain.payment.CardDetails;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Map;

/**
 * Pasarela de tarjetas de PRUEBAS: no cobra nada ni contacta con ningún banco. Responde como una
 * pasarela real según tarjetas de prueba conocidas, para demostrar los flujos aprobado y rechazado.
 * En producción se sustituye por el adaptador de una pasarela real (Niubiz, Izipay, Culqi, Mercado Pago)
 * que tokeniza la tarjeta en el navegador: el puerto CardGateway no cambia.
 */
public final class SandboxCardGateway implements CardGateway {
    /** Tarjetas de prueba que se rechazan (número completo → motivo). Cualquier otra tarjeta válida se aprueba. */
    public static final Map<String, String> DECLINED = Map.of(
            "4000000000000002", "INSUFFICIENT_FUNDS",
            "4000000000000069", "CARD_BLOCKED",
            "5105105105105100", "DO_NOT_HONOR");
    private final SecureRandom random = new SecureRandom();

    @Override
    public Result charge(CardDetails card, BigDecimal amount, String currency, String reference) {
        String declined = DECLINED.get(card.number());
        if (declined != null) return Result.declined(declined);
        return Result.approved("AUT-" + String.format("%06d", random.nextInt(1_000_000)));
    }
}
