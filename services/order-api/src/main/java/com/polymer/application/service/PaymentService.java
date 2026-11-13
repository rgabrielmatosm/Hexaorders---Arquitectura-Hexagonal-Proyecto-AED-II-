package com.polymer.application.service;

import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ForbiddenOperationException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.PaymentUseCases;
import com.polymer.application.port.in.PaymentView;
import com.polymer.application.port.in.ReceiptView;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CardGateway;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.PaymentRepository;
import com.polymer.application.port.out.PaymentWindow;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.port.out.WalletGateway;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.OrderCheckout;
import com.polymer.domain.fulfillment.PaymentMethod;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.payment.CardDetails;
import com.polymer.domain.payment.Payment;
import com.polymer.domain.payment.PaymentPolicy;
import com.polymer.domain.payment.PaymentStatus;
import com.polymer.domain.payment.Receipt;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Pago en línea de un pedido de la tienda. Tarjeta: autorización inmediata. Yape/Plin: una solicitud
 * con QR que vive 60 s en la ventana de pago (Redis con TTL o el reloj); vencida, hay que generar otra.
 * Un pago aprobado emite la boleta; el correo con la boleta lo envía el consumidor de eventos.
 */
public final class PaymentService implements PaymentUseCases {
    private final CommerceOrderRepository orders;
    private final PaymentRepository payments;
    private final CardGateway cards;
    private final WalletGateway wallets;
    private final PaymentWindow window;
    private final UserRepository users;
    private final BusinessTelemetry telemetry;
    private final AuditLog audit;
    private final Clock clock;
    private final ZoneId zone;
    private final boolean sandbox;

    public PaymentService(CommerceOrderRepository orders, PaymentRepository payments, CardGateway cards, WalletGateway wallets,
                          PaymentWindow window, UserRepository users, BusinessTelemetry telemetry, AuditLog audit, Clock clock,
                          ZoneId zone, boolean sandbox) {
        this.orders = orders;
        this.payments = payments;
        this.cards = cards;
        this.wallets = wallets;
        this.window = window;
        this.users = users;
        this.telemetry = telemetry;
        this.audit = audit;
        this.clock = clock;
        this.zone = zone;
        this.sandbox = sandbox;
    }

    @Override
    public YearMonth currentMonth() { return YearMonth.now(clock.withZone(zone)); }

    @Override
    public PaymentView status(AuthenticatedUser customer, UUID orderId) {
        CommerceOrder order = ownedOrder(customer, orderId);
        List<Payment> history = expireIfElapsed(payments.forOrder(orderId));
        return view(order, history);
    }

    @Override
    public PaymentView payWithCard(AuthenticatedUser customer, UUID orderId, CardDetails card) {
        CommerceOrder order = payableOrder(customer, orderId);
        cancelPending(orderId);
        Instant now = clock.instant();
        Payment payment = Payment.card(UUID.randomUUID(), orderId, order.amount(), order.currency(), card, now);
        CardGateway.Result result = cards.charge(card, payment.amount(), payment.currency(), "POLYMER-" + shortId(orderId));
        Payment settled = result.approved()
                ? payment.approved(result.authorization(), Receipt.issue(payments.nextReceiptNumber(), payment.amount()), now)
                : payment.declined(result.declineCode());
        payments.save(settled);
        finish(customer, settled);
        return status(customer, orderId);
    }

    @Override
    public PaymentView startWallet(AuthenticatedUser customer, UUID orderId, PaymentMethod wallet) {
        if (wallet != PaymentMethod.YAPE && wallet != PaymentMethod.PLIN) throw new IllegalArgumentException("method: YAPE o PLIN");
        CommerceOrder order = payableOrder(customer, orderId);
        cancelPending(orderId);
        Payment payment = Payment.wallet(UUID.randomUUID(), orderId, wallet, order.amount(), order.currency(), clock.instant());
        payments.save(payment);
        window.open(payment.id(), PaymentPolicy.WALLET_TTL);
        telemetry.payment(wallet.name(), "started");
        audit.record(customer.username(), "PAYMENT_REQUESTED", "order:" + orderId, wallet.name() + " · 60 s");
        return status(customer, orderId);
    }

    @Override
    public PaymentView confirmWallet(AuthenticatedUser customer, UUID orderId, UUID paymentId, String phone, String approvalCode) {
        ownedOrder(customer, orderId);
        Payment payment = payments.find(paymentId).filter(p -> p.orderId().equals(orderId))
                .orElseThrow(() -> new ResourceNotFoundException("Solicitud de pago no encontrada"));
        if (payment.status() == PaymentStatus.PAID) throw new BusinessConflictException("ALREADY_PAID", "El pedido ya está pagado");
        if (payment.status() == PaymentStatus.EXPIRED || (payment.status() == PaymentStatus.PENDING && !window.isOpen(paymentId, payment.expiresAt()))) {
            if (payment.status() == PaymentStatus.PENDING) {
                payments.save(payment.expired());
                telemetry.payment(payment.method().name(), "expired");
            }
            throw new BusinessConflictException("PAYMENT_EXPIRED", "La solicitud de pago venció: genera un código QR nuevo");
        }
        if (payment.status() != PaymentStatus.PENDING) throw new BusinessConflictException("PAYMENT_CLOSED", "Esta solicitud de pago ya no está activa");
        String cell = phone == null ? "" : phone.replaceAll("[\\s-]", "").replaceFirst("^\\+?51", "");
        if (!cell.matches("9\\d{8}")) throw new IllegalArgumentException("phone: celular de 9 dígitos que empiece con 9");
        String code = approvalCode == null ? "" : approvalCode.strip();
        if (!code.matches("\\d{6}")) throw new IllegalArgumentException("approvalCode: el código de aprobación tiene 6 dígitos");
        CardGateway.Result result = wallets.confirm(payment.method(), paymentId, cell, code, payment.amount());
        Instant now = clock.instant();
        Payment settled = result.approved()
                ? payment.approved(result.authorization(), Receipt.issue(payments.nextReceiptNumber(), payment.amount()), now)
                : payment.declined(result.declineCode());
        payments.save(settled);
        window.close(paymentId);
        finish(customer, settled);
        return status(customer, orderId);
    }

    @Override
    public ReceiptView receipt(AuthenticatedUser customer, UUID orderId) {
        CommerceOrder order = ownedOrder(customer, orderId);
        Payment paid = payments.forOrder(orderId).stream().filter(p -> p.status() == PaymentStatus.PAID).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("El pedido aún no tiene boleta (no está pagado en línea)"));
        UserAccount account = users.findById(customer.userId()).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Optional<OrderCheckout> checkout = orders.checkoutOf(orderId);
        Receipt receipt = paid.receipt();
        BigDecimal subtotal = checkout.map(OrderCheckout::subtotal).orElse(order.amount());
        return new ReceiptView(receipt.code(), paid.paidAt(), orderId, account.displayName(), account.email(), order.lines(), subtotal,
                checkout.map(OrderCheckout::savings).orElse(BigDecimal.ZERO), checkout.map(OrderCheckout::shippingCost).orElse(BigDecimal.ZERO),
                receipt.taxableBase(), receipt.igv(), receipt.total(), paid.currency(), paid.method().name(), paid.reference(),
                paid.cardBrand(), paid.cardLast4(), checkout.map(c -> c.delivery().name()).orElse(null), orders.shippingOf(orderId).orElse(null));
    }

    // ---- internos -----------------------------------------------------------------------------------

    private CommerceOrder ownedOrder(AuthenticatedUser customer, UUID orderId) {
        if (customer == null) throw new ForbiddenOperationException("Sesión requerida");
        if (orderId == null) throw new IllegalArgumentException("orderId: obligatorio");
        Optional<Long> owner = orders.ownerOf(orderId);
        if (owner.isEmpty() || owner.get() != customer.userId()) throw new ResourceNotFoundException("Pedido no encontrado");
        return orders.findById(orderId).orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
    }

    private CommerceOrder payableOrder(AuthenticatedUser customer, UUID orderId) {
        CommerceOrder order = ownedOrder(customer, orderId);
        if (order.status() == FulfillmentStatus.CANCELLED || order.status() == FulfillmentStatus.REJECTED) {
            throw new BusinessConflictException("ORDER_CLOSED", "El pedido está cancelado o no fue aprobado: no se puede pagar");
        }
        List<Payment> history = payments.forOrder(orderId);
        if (history.stream().anyMatch(p -> p.status() == PaymentStatus.PAID)) throw new BusinessConflictException("ALREADY_PAID", "El pedido ya está pagado");
        long declined = history.stream().filter(p -> p.status() == PaymentStatus.DECLINED).count();
        if (declined >= PaymentPolicy.MAX_DECLINED_ATTEMPTS) {
            throw new BusinessConflictException("TOO_MANY_DECLINES", "Demasiados pagos rechazados: elige otro método o escríbenos");
        }
        return order;
    }

    /** Una solicitud de Yape/Plin pendiente se anula al generar otra o pagar con tarjeta. */
    private void cancelPending(UUID orderId) {
        for (Payment pending : payments.forOrder(orderId)) {
            if (pending.status() == PaymentStatus.PENDING) {
                payments.save(pending.cancelled());
                window.close(pending.id());
            }
        }
    }

    private List<Payment> expireIfElapsed(List<Payment> history) {
        return history.stream().map(p -> {
            if (p.status() == PaymentStatus.PENDING && p.expiresAt() != null && !window.isOpen(p.id(), p.expiresAt())) {
                Payment expired = p.expired();
                payments.save(expired);
                telemetry.payment(p.method().name(), "expired");
                return expired;
            }
            return p;
        }).toList();
    }

    private void finish(AuthenticatedUser customer, Payment settled) {
        boolean paid = settled.status() == PaymentStatus.PAID;
        telemetry.payment(settled.method().name(), paid ? "paid" : "declined");
        if (paid) payments.recordMethodUsed(settled.orderId(), settled.method());
        audit.record(customer.username(), paid ? "PAYMENT_APPROVED" : "PAYMENT_DECLINED", "order:" + settled.orderId(),
                settled.method() + (paid ? " · " + settled.receipt().code() : " · " + settled.failureCode())
                        + (settled.cardLast4() == null ? "" : " · " + settled.cardBrand() + " " + settled.cardLast4()));
    }

    private PaymentView view(CommerceOrder order, List<Payment> history) {
        Optional<OrderCheckout> checkout = orders.checkoutOf(order.id());
        PaymentMethod chosen = checkout.map(OrderCheckout::payment).orElse(null);
        // Prioridad: el pago aprobado; si no, la solicitud vigente (solo hay una); si no, el intento más reciente.
        Payment latest = history.stream().filter(p -> p.status() == PaymentStatus.PAID).findFirst()
                .or(() -> history.stream().filter(p -> p.status() == PaymentStatus.PENDING).findFirst())
                .orElse(history.isEmpty() ? null : history.get(history.size() - 1));
        int declined = (int) history.stream().filter(p -> p.status() == PaymentStatus.DECLINED).count();
        if (latest == null) {
            return new PaymentView(order.id(), chosen == null ? null : chosen.name(), chosen != null && chosen.online(), order.amount(),
                    order.currency(), null, null, null, null, 0, null, null, null, null, null, null, null, declined, sandbox);
        }
        boolean pendingWallet = latest.status() == PaymentStatus.PENDING && latest.expiresAt() != null;
        return new PaymentView(order.id(), chosen == null ? null : chosen.name(), chosen != null && chosen.online(), order.amount(),
                order.currency(), latest.id(), latest.status(), latest.method().name(), latest.expiresAt(),
                pendingWallet ? window.remainingSeconds(latest.id(), latest.expiresAt()) : 0,
                pendingWallet ? wallets.qrPayload(latest.method(), latest.id(), latest.amount(), latest.currency()) : null,
                latest.reference(), latest.cardBrand(), latest.cardLast4(), latest.failureCode(), latest.paidAt(),
                latest.receipt() == null ? null : latest.receipt().code(), declined, sandbox);
    }

    private static String shortId(UUID id) { return id.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT); }
}
