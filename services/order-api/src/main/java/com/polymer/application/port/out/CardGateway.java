package com.polymer.application.port.out;

import com.polymer.domain.payment.CardDetails;
import java.math.BigDecimal;

/** Pasarela de tarjetas (autorización y captura en un paso). */
public interface CardGateway {
    Result charge(CardDetails card, BigDecimal amount, String currency, String reference);

    /** approved: código de autorización; si no, declineCode (INSUFFICIENT_FUNDS, CARD_BLOCKED…). */
    record Result(boolean approved, String authorization, String declineCode) {
        public static Result approved(String authorization) { return new Result(true, authorization, null); }
        public static Result declined(String code) { return new Result(false, null, code); }
    }
}
