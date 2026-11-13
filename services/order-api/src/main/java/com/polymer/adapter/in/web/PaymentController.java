package com.polymer.adapter.in.web;

import com.polymer.adapter.in.web.security.CurrentUser;
import com.polymer.application.port.in.PaymentUseCases;
import com.polymer.application.port.in.PaymentView;
import com.polymer.application.port.in.ReceiptView;
import com.polymer.domain.fulfillment.PaymentMethod;
import com.polymer.domain.payment.CardDetails;
import com.polymer.domain.payment.PaymentStatus;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v7 · Pago en línea de un pedido del cliente (tarjeta, Yape, Plin) y su boleta. Solo el dueño del
 * pedido (rol CUSTOMER, /api/shop/**). Un pago rechazado responde 402 con el motivo.
 */
@RestController
@RequestMapping("/api/shop/orders/{orderId}")
public final class PaymentController {
    private final PaymentUseCases payments;

    public PaymentController(PaymentUseCases payments) { this.payments = payments; }

    @GetMapping("/payment")
    public PaymentView status(@PathVariable UUID orderId) { return payments.status(CurrentUser.require(), orderId); }

    @PostMapping("/payment/card")
    public ResponseEntity<PaymentView> card(@PathVariable UUID orderId, @RequestBody CardRequest request) {
        // Los datos de la tarjeta solo viven en esta petición: no se registran ni se guardan.
        CardDetails card = CardDetails.of(request.number(), request.holder(), request.expMonth(), request.expYear(), request.cvv(),
                payments.currentMonth());
        return settled(payments.payWithCard(CurrentUser.require(), orderId, card));
    }

    @PostMapping("/payment/wallet")
    public ResponseEntity<PaymentView> wallet(@PathVariable UUID orderId, @RequestBody WalletRequest request) {
        PaymentMethod method = PaymentMethod.parse(request.method());
        return ResponseEntity.status(HttpStatus.CREATED).body(payments.startWallet(CurrentUser.require(), orderId, method));
    }

    @PostMapping("/payment/wallet/{paymentId}/confirm")
    public ResponseEntity<PaymentView> confirm(@PathVariable UUID orderId, @PathVariable UUID paymentId, @RequestBody ConfirmRequest request) {
        return settled(payments.confirmWallet(CurrentUser.require(), orderId, paymentId, request.phone(), request.approvalCode()));
    }

    @GetMapping("/receipt")
    public ReceiptView receipt(@PathVariable UUID orderId) { return payments.receipt(CurrentUser.require(), orderId); }

    private static ResponseEntity<PaymentView> settled(PaymentView view) {
        return ResponseEntity.status(view.status() == PaymentStatus.DECLINED ? HttpStatus.PAYMENT_REQUIRED : HttpStatus.OK).body(view);
    }

    public record CardRequest(String number, String holder, Integer expMonth, Integer expYear, String cvv) {
        @Override
        public String toString() { return "CardRequest[oculto]"; }
    }
    public record WalletRequest(String method) {}
    public record ConfirmRequest(String phone, String approvalCode) {}
}
