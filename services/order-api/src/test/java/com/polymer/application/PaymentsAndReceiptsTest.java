package com.polymer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.polymer.adapter.out.payment.ClockPaymentWindow;
import com.polymer.adapter.out.payment.SandboxCardGateway;
import com.polymer.adapter.out.payment.SandboxWalletGateway;
import com.polymer.application.exception.BusinessConflictException;
import com.polymer.application.exception.ResourceNotFoundException;
import com.polymer.application.port.in.AuthenticatedUser;
import com.polymer.application.port.in.PaymentView;
import com.polymer.application.port.in.ReceiptView;
import com.polymer.application.port.in.TodayView;
import com.polymer.application.port.out.AuditLog;
import com.polymer.application.port.out.BusinessTelemetry;
import com.polymer.application.port.out.CommerceOrderRepository;
import com.polymer.application.port.out.IntegrationTelemetry;
import com.polymer.application.port.out.MailSender;
import com.polymer.application.port.out.NotificationRecipients;
import com.polymer.application.port.out.PaymentRepository;
import com.polymer.application.port.out.ProcessedEvents;
import com.polymer.application.port.out.ReceiptSource;
import com.polymer.application.port.out.UserRepository;
import com.polymer.application.service.NotificationService;
import com.polymer.application.service.PaymentService;
import com.polymer.application.service.StoreService;
import com.polymer.domain.OrderStatus;
import com.polymer.domain.event.BusinessEvent;
import com.polymer.domain.fulfillment.CommerceOrder;
import com.polymer.domain.fulfillment.DeliveryMethod;
import com.polymer.domain.fulfillment.FulfillmentStatus;
import com.polymer.domain.fulfillment.OrderCheckout;
import com.polymer.domain.fulfillment.OrderLine;
import com.polymer.domain.fulfillment.PaymentMethod;
import com.polymer.domain.identity.Language;
import com.polymer.domain.identity.Role;
import com.polymer.domain.identity.UserAccount;
import com.polymer.domain.payment.CardDetails;
import com.polymer.domain.payment.Payment;
import com.polymer.domain.payment.PaymentStatus;
import com.polymer.domain.store.BusinessHours;
import com.polymer.domain.store.StoreProfile;
import com.polymer.support.MutableClock;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** v7: pagos con tarjeta (inmediato), Yape/Plin (60 s), boleta con IGV, correo de la boleta y horario de la tienda. */
class PaymentsAndReceiptsTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z"); // viernes 10:00 en Lima
    private static final UUID ORDER = UUID.fromString("7a1c0de0-0000-4000-8000-000000000001");
    private static final AuthenticatedUser CUSTOMER = new AuthenticatedUser(42, "cliente", EnumSet.of(Role.CUSTOMER));
    private static final AuthenticatedUser OTHER = new AuthenticatedUser(43, "otro", EnumSet.of(Role.CUSTOMER));
    private static final YearMonth MONTH = YearMonth.of(2026, 11);

    private MutableClock clock;
    private CommerceOrderRepository orders;
    private InMemoryPayments payments;
    private BusinessTelemetry telemetry;
    private PaymentService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        orders = mock(CommerceOrderRepository.class);
        payments = new InMemoryPayments();
        telemetry = mock(BusinessTelemetry.class);
        UserRepository users = mock(UserRepository.class);
        when(users.findById(42)).thenReturn(Optional.of(new UserAccount(42, "cliente", "Cliente Demo", "cliente@gmail.com",
                EnumSet.of(Role.CUSTOMER), true, NOW)));
        order(FulfillmentStatus.RISK_APPROVED, "118.00");
        service = new PaymentService(orders, payments, new SandboxCardGateway(), new SandboxWalletGateway(), new ClockPaymentWindow(clock),
                users, telemetry, mock(AuditLog.class), clock, ZoneId.of("America/Lima"), true);
    }

    private void order(FulfillmentStatus status, String amount) {
        CommerceOrder order = new CommerceOrder(ORDER, "u42", new BigDecimal(amount), "PEN", 10, OrderStatus.APPROVED, status, "SHOP", 0, NOW,
                List.of(new OrderLine(1, 5, "Mouse inalámbrico", new BigDecimal(amount), 1, new BigDecimal(amount), null, null)), 100123L);
        when(orders.ownerOf(ORDER)).thenReturn(Optional.of(42L));
        when(orders.findById(ORDER)).thenReturn(Optional.of(order));
        when(orders.checkoutOf(ORDER)).thenReturn(Optional.of(new OrderCheckout(DeliveryMethod.PICKUP, PaymentMethod.CARD, new BigDecimal(amount),
                BigDecimal.ZERO.setScale(2), BigDecimal.ZERO.setScale(2), new BigDecimal(amount))));
    }

    private static CardDetails card(String number) { return CardDetails.of(number, "Cliente Demo", 12, 2030, "123", MONTH); }

    @Test
    void anApprovedCardPaysInstantlyIssuesTheReceiptAndKeepsOnlyTheLastFourDigits() {
        PaymentView view = service.payWithCard(CUSTOMER, ORDER, card("4111111111111111"));
        assertThat(view.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(view.receipt()).isEqualTo("B001-00000001");
        assertThat(view.cardLast4()).isEqualTo("1111");
        assertThat(view.reference()).startsWith("AUT-");
        Payment stored = payments.byId.values().iterator().next();
        assertThat(stored.toString()).doesNotContain("4111111111111111");
        verify(telemetry).payment("CARD", "paid");
        ReceiptView receipt = service.receipt(CUSTOMER, ORDER);
        assertThat(receipt.code()).isEqualTo("B001-00000001");
        assertThat(receipt.taxableBase()).isEqualByComparingTo("100.00");
        assertThat(receipt.igv()).isEqualByComparingTo("18.00");
        assertThat(receipt.customerEmail()).isEqualTo("cliente@gmail.com");
        assertThatThrownBy(() -> service.payWithCard(CUSTOMER, ORDER, card("4111111111111111"))).hasMessageContaining("ya está pagado");
    }

    @Test
    void aDeclinedCardIsRecordedAndFiveDeclinesBlockOnlinePayment() {
        for (int i = 0; i < 5; i++) {
            PaymentView view = service.payWithCard(CUSTOMER, ORDER, card("4000000000000002"));
            assertThat(view.status()).isEqualTo(PaymentStatus.DECLINED);
            assertThat(view.failureCode()).isEqualTo("INSUFFICIENT_FUNDS");
        }
        assertThatThrownBy(() -> service.payWithCard(CUSTOMER, ORDER, card("4111111111111111")))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("Demasiados");
        assertThatThrownBy(() -> service.receipt(CUSTOMER, ORDER)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void yapeRequestsExpireAfterSixtySecondsAndANewOneCanBePaid() {
        PaymentView started = service.startWallet(CUSTOMER, ORDER, PaymentMethod.YAPE);
        assertThat(started.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(started.remainingSeconds()).isEqualTo(60);
        assertThat(started.qrPayload()).startsWith("POLYMER-SANDBOX|YAPE|").contains("118.00");
        clock.advance(Duration.ofSeconds(45));
        assertThat(service.status(CUSTOMER, ORDER).remainingSeconds()).isEqualTo(15);
        clock.advance(Duration.ofSeconds(15));
        assertThatThrownBy(() -> service.confirmWallet(CUSTOMER, ORDER, started.paymentId(), "987654321", "123456"))
                .isInstanceOf(BusinessConflictException.class).hasMessageContaining("venció");
        assertThat(service.status(CUSTOMER, ORDER).status()).isEqualTo(PaymentStatus.EXPIRED);
        verify(telemetry).payment("YAPE", "expired");
        PaymentView fresh = service.startWallet(CUSTOMER, ORDER, PaymentMethod.YAPE);
        clock.advance(Duration.ofSeconds(20));
        PaymentView paid = service.confirmWallet(CUSTOMER, ORDER, fresh.paymentId(), "+51 987 654 321", "123456");
        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.reference()).startsWith("YAPE-");
    }

    @Test
    void walletRulesPhoneApprovalCodeDeclineAndReplacement() {
        PaymentView plin = service.startWallet(CUSTOMER, ORDER, PaymentMethod.PLIN);
        assertThatThrownBy(() -> service.confirmWallet(CUSTOMER, ORDER, plin.paymentId(), "812345678", "123456")).hasMessageContaining("phone");
        assertThatThrownBy(() -> service.confirmWallet(CUSTOMER, ORDER, plin.paymentId(), "912345678", "12")).hasMessageContaining("approvalCode");
        // Una solicitud nueva anula la anterior.
        PaymentView yape = service.startWallet(CUSTOMER, ORDER, PaymentMethod.YAPE);
        assertThat(payments.byId.get(plin.paymentId()).status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThatThrownBy(() -> service.confirmWallet(CUSTOMER, ORDER, plin.paymentId(), "912345678", "123456")).hasMessageContaining("ya no está activa");
        PaymentView declined = service.confirmWallet(CUSTOMER, ORDER, yape.paymentId(), "912345678", "000000");
        assertThat(declined.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(declined.failureCode()).isEqualTo("WRONG_APPROVAL_CODE");
    }

    @Test
    void onlyTheOwnerCanPayAndClosedOrdersCannotBePaid() {
        assertThatThrownBy(() -> service.status(OTHER, ORDER)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.payWithCard(OTHER, ORDER, card("4111111111111111"))).isInstanceOf(ResourceNotFoundException.class);
        order(FulfillmentStatus.CANCELLED, "118.00");
        assertThatThrownBy(() -> service.startWallet(CUSTOMER, ORDER, PaymentMethod.YAPE)).hasMessageContaining("cancelado");
    }

    @Test
    void walletsHaveAnAmountLimit() {
        order(FulfillmentStatus.RISK_APPROVED, "2599.00");
        assertThatThrownBy(() -> service.startWallet(CUSTOMER, ORDER, PaymentMethod.YAPE)).hasMessageContaining("2000");
        assertThat(service.payWithCard(CUSTOMER, ORDER, card("5555555555554444")).status()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void aConfirmedPaymentEventSendsTheReceiptByEmailInTheCustomersLanguage() {
        MailSender mail = mock(MailSender.class);
        ProcessedEvents processed = mock(ProcessedEvents.class);
        NotificationRecipients recipient = orderId -> Optional.of(new NotificationRecipients.Recipient("Ana", "ana@gmail.com", Language.EN));
        ReceiptSource receipts = orderId -> Optional.of(new ReceiptSource.ReceiptMail("B001-00000007", NOW, "YAPE", "YAPE-12345678", null, null,
                List.of(new ReceiptSource.Line("Mouse inalámbrico", 2, new BigDecimal("59.00"), new BigDecimal("118.00"))),
                new BigDecimal("118.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), new BigDecimal("18.00"),
                new BigDecimal("118.00"), "PEN", "PICKUP"));
        var notifications = new NotificationService(processed, recipient, mail, IntegrationTelemetry.NONE, receipts, "http://localhost:8080", true);
        assertThat(notifications.handle(BusinessEvent.fromPayment(UUID.randomUUID(), ORDER, "YAPE", "B001-00000007", NOW))).isEqualTo("sent");
        var sent = ArgumentCaptor.forClass(MailSender.MailMessage.class);
        verify(mail).send(sent.capture());
        assertThat(sent.getValue().to()).isEqualTo("ana@gmail.com");
        assertThat(sent.getValue().subject()).isEqualTo("Receipt B001-00000007 · payment received for order 7a1c0de0");
        assertThat(sent.getValue().body()).contains("S/ 100.00").contains("S/ 18.00").contains("IGV (18%)")
                .contains("http://localhost:8080/en/").contains(ORDER.toString());
        assertThat(sent.getValue().html()).contains("<table").contains("B001-00000007").contains("Mouse inalámbrico");
        verify(processed).markProcessed(any(), any());
    }

    @Test
    void theStoreKnowsTodaysHoursInLimaAndBuildsMapsAndCalendarLinks() {
        StoreProfile profile = new StoreProfile("POLYMER", "Av. Javier Prado Este 4200", "Santiago de Surco", "Lima", "Perú",
                -12.0853, -76.9753, "+51 900 000 000", "ventas@polymer.local");
        StoreService store = new StoreService(profile, BusinessHours.standard(), Clock.fixed(NOW, ZoneId.of("UTC")), ZoneId.of("America/Lima"), "");
        TodayView today = store.today();
        assertThat(today.date()).isEqualTo("2026-11-13");
        assertThat(today.weekday()).isEqualTo(5);
        assertThat(today.now()).isEqualTo("10:00");
        assertThat(today.openNow()).isTrue();
        assertThat(today.opens()).isEqualTo("09:00");
        assertThat(today.closes()).isEqualTo("19:00");
        assertThat(today.googleCalendarUrl()).startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE")
                .contains("dates=20261113T153000Z/20261113T163000Z").contains("ctz=America%2FLima");
        var view = store.store();
        assertThat(view.mapsEmbedUrl()).isEqualTo("https://www.google.com/maps?q=-12.085300,-76.975300&z=16&hl=es&output=embed");
        assertThat(view.mapsDirectionsUrl()).isEqualTo("https://www.google.com/maps/dir/?api=1&destination=-12.085300,-76.975300");
        assertThat(view.week()).hasSize(7);
        assertThat(view.week().get(6).opens()).isNull(); // domingo cerrado
        assertThat(view.calendarEmbedUrl()).isNull();
        StoreService sunday = new StoreService(profile, BusinessHours.standard(), Clock.fixed(Instant.parse("2026-11-15T16:00:00Z"), ZoneId.of("UTC")),
                ZoneId.of("America/Lima"), "polymer@group.calendar.google.com");
        assertThat(sunday.today().openNow()).isFalse();
        assertThat(sunday.today().nextOpening()).isEqualTo("2026-11-16T09:00");
        assertThat(sunday.store().calendarEmbedUrl()).contains("calendar/embed?src=polymer%40group.calendar.google.com");
    }

    static final class InMemoryPayments implements PaymentRepository {
        final Map<UUID, Payment> byId = new LinkedHashMap<>();
        final AtomicLong receipts = new AtomicLong();

        @Override public void save(Payment payment) {
            if (payment.status() == PaymentStatus.PAID && byId.values().stream()
                    .anyMatch(p -> p.orderId().equals(payment.orderId()) && p.status() == PaymentStatus.PAID && !p.id().equals(payment.id()))) {
                throw new BusinessConflictException("ALREADY_PAID", "El pedido ya está pagado");
            }
            byId.put(payment.id(), payment);
        }
        @Override public Optional<Payment> find(UUID id) { return Optional.ofNullable(byId.get(id)); }
        @Override public List<Payment> forOrder(UUID orderId) { return new ArrayList<>(byId.values().stream().filter(p -> p.orderId().equals(orderId)).toList()); }
        @Override public long nextReceiptNumber() { return receipts.incrementAndGet(); }
        @Override public void recordMethodUsed(UUID orderId, PaymentMethod method) {}
    }
}
