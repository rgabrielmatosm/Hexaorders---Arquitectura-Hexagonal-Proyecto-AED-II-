package com.polymer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.polymer.domain.fulfillment.DeliveryMethod;
import com.polymer.domain.fulfillment.PaymentMethod;
import com.polymer.domain.identity.ExternalIdentity;
import com.polymer.domain.identity.VerificationChallenge;
import com.polymer.domain.identity.VerificationPolicy;
import com.polymer.domain.identity.VerificationPurpose;
import com.polymer.domain.payment.CardBrand;
import com.polymer.domain.payment.CardDetails;
import com.polymer.domain.payment.Payment;
import com.polymer.domain.payment.PaymentPolicy;
import com.polymer.domain.payment.PaymentStatus;
import com.polymer.domain.payment.Receipt;
import com.polymer.domain.store.BusinessHours;
import com.polymer.domain.store.PeruHolidays;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** v7: reglas de dominio del código por correo, de los pagos en línea, de la boleta y del horario de la tienda. */
class SignInPaymentStoreDomainTest {
    private static final Instant NOW = Instant.parse("2026-11-13T15:00:00Z");
    private static final YearMonth TODAY = YearMonth.of(2026, 11);

    // ---- Código de verificación -------------------------------------------------------------------------

    @Test
    void codesHaveSixDigitsIncludingLeadingZerosAndAreNormalized() {
        SplittableRandom random = new SplittableRandom(7);
        for (int i = 0; i < 200; i++) assertThat(VerificationPolicy.newCode(random)).matches("\\d{6}");
        assertThat(VerificationPolicy.wellFormed("123 456")).isTrue();
        assertThat(VerificationPolicy.wellFormed("123-456")).isTrue();
        assertThat(VerificationPolicy.wellFormed("12345")).isFalse();
        assertThat(VerificationPolicy.wellFormed("12a456")).isFalse();
        assertThat(VerificationPolicy.maskEmail("carla.diaz@gmail.com")).isEqualTo("ca•••@gmail.com");
        assertThat(VerificationPolicy.maskEmail("a@b.pe")).isEqualTo("a•••@b.pe");
    }

    @Test
    void aChallengeExpiresAfterTenMinutesAndAllowsFiveAttempts() {
        VerificationChallenge challenge = VerificationChallenge.issue(UUID.randomUUID(), 7, VerificationPurpose.SIGN_IN, "hash", NOW);
        assertThat(challenge.usable(NOW.plus(Duration.ofMinutes(9)))).isTrue();
        assertThat(challenge.expired(NOW.plus(VerificationPolicy.CODE_TTL))).isTrue();
        VerificationChallenge failing = challenge;
        for (int i = 0; i < VerificationPolicy.MAX_ATTEMPTS; i++) failing = failing.afterFailedAttempt();
        assertThat(failing.attemptsLeft()).isZero();
        assertThat(failing.usable(NOW)).isFalse();
        assertThat(challenge.consume().usable(NOW)).isFalse();
    }

    @Test
    void resendingWaitsSixtySecondsAndRestartsValidityAndAttempts() {
        VerificationChallenge challenge = VerificationChallenge.issue(UUID.randomUUID(), 7, VerificationPurpose.SIGN_IN, "h1", NOW)
                .afterFailedAttempt();
        assertThat(challenge.canResend(NOW.plusSeconds(30))).isFalse();
        assertThat(challenge.resendAvailableIn(NOW.plusSeconds(30))).isEqualTo(Duration.ofSeconds(30));
        assertThat(challenge.canResend(NOW.plusSeconds(60))).isTrue();
        VerificationChallenge resent = challenge.resent("h2", NOW.plusSeconds(60));
        assertThat(resent.attemptsLeft()).isEqualTo(VerificationPolicy.MAX_ATTEMPTS);
        assertThat(resent.expiresAt()).isEqualTo(NOW.plusSeconds(60).plus(VerificationPolicy.CODE_TTL));
        assertThat(resent.sends()).isEqualTo(2);
    }

    @Test
    void googleIdentitySuggestsAValidUsernameFromTheEmail() {
        ExternalIdentity identity = new ExternalIdentity("google", "1234567890", "Ana.Pérez+tienda@Gmail.com", true, "Ana Pérez");
        assertThat(identity.email()).isEqualTo("ana.pérez+tienda@gmail.com");
        assertThat(identity.suggestedUsername()).matches("[a-z0-9_-]{3,24}").isEqualTo("ana_perez_tienda");
        assertThat(new ExternalIdentity("google", "1", "x@b.pe", true, null).suggestedUsername()).hasSizeGreaterThanOrEqualTo(3);
        assertThatThrownBy(() -> new ExternalIdentity("google", "1", "no-es-correo", true, "X")).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- Tarjetas ----------------------------------------------------------------------------------------

    @Test
    void cardsAreValidatedWithLuhnExpiryAndCvvAndNeverPrintTheNumber() {
        CardDetails visa = CardDetails.of("4111 1111 1111 1111", "Ana Pérez", 12, 30, "123", TODAY);
        assertThat(visa.brand()).isEqualTo(CardBrand.VISA);
        assertThat(visa.last4()).isEqualTo("1111");
        assertThat(visa.toString()).isEqualTo("VISA •••• 1111").doesNotContain("4111 1111").doesNotContain("123");
        assertThat(CardDetails.of("5555-5555-5555-4444", "Ana", 1, 2027, "999", TODAY).brand()).isEqualTo(CardBrand.MASTERCARD);
        assertThat(CardDetails.of("378282246310005", "Ana Amex", 1, 2027, "1234", TODAY).brand()).isEqualTo(CardBrand.AMEX);
        assertThatThrownBy(() -> CardDetails.of("4111111111111112", "Ana", 12, 30, "123", TODAY)).hasMessageContaining("cardNumber");
        assertThatThrownBy(() -> CardDetails.of("4111111111111111", "Ana", 10, 26, "123", TODAY)).hasMessageContaining("vencida");
        assertThatThrownBy(() -> CardDetails.of("378282246310005", "Ana", 1, 27, "123", TODAY)).hasMessageContaining("cvv");
        assertThatThrownBy(() -> CardDetails.of("4111111111111111", "A", 1, 27, "123", TODAY)).hasMessageContaining("cardHolder");
        assertThat(CardDetails.of("4111111111111111", "Ana", 11, 26, "123", TODAY).expiry()).isEqualTo(TODAY); // vence a fin de mes
    }

    // ---- Pagos y boleta -------------------------------------------------------------------------------------

    @Test
    void walletRequestsLiveSixtySecondsAndHaveALimit() {
        Payment yape = Payment.wallet(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.YAPE, new BigDecimal("150.00"), "PEN", NOW);
        assertThat(yape.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(yape.expiresAt()).isEqualTo(NOW.plus(PaymentPolicy.WALLET_TTL)).isEqualTo(NOW.plusSeconds(60));
        assertThat(yape.windowElapsed(NOW.plusSeconds(59))).isFalse();
        assertThat(yape.windowElapsed(NOW.plusSeconds(60))).isTrue();
        assertThat(yape.expired().status()).isEqualTo(PaymentStatus.EXPIRED);
        assertThatThrownBy(() -> Payment.wallet(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PLIN, new BigDecimal("2000.01"), "PEN", NOW))
                .hasMessageContaining("2000");
        assertThatThrownBy(() -> Payment.wallet(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.CARD, BigDecimal.TEN, "PEN", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anApprovedPaymentCarriesAReceiptWhoseBasePlusIgvIsTheTotal() {
        for (String total : new String[] {"118.00", "4599.90", "0.01", "1234.57", "29.90"}) {
            Receipt receipt = Receipt.issue(123, new BigDecimal(total));
            assertThat(receipt.taxableBase().add(receipt.igv())).isEqualByComparingTo(total);
            assertThat(receipt.code()).isEqualTo("B001-00000123");
        }
        assertThat(Receipt.issue(1, new BigDecimal("118.00")).igv()).isEqualByComparingTo("18.00");
        CardDetails card = CardDetails.of("4111111111111111", "Ana", 12, 30, "123", TODAY);
        Payment pending = Payment.card(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("118.00"), "PEN", card, NOW);
        Payment paid = pending.approved("AUT-1", Receipt.issue(1, new BigDecimal("118.00")), NOW);
        assertThat(paid.status()).isEqualTo(PaymentStatus.PAID);
        assertThat(paid.cardLast4()).isEqualTo("1111");
        assertThatThrownBy(() -> paid.approved("AUT-2", Receipt.issue(2, new BigDecimal("118.00")), NOW)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onlineMethodsWorkWithEveryDeliveryMethod() {
        for (PaymentMethod method : new PaymentMethod[] {PaymentMethod.CARD, PaymentMethod.YAPE, PaymentMethod.PLIN}) {
            assertThat(method.online()).isTrue();
            for (DeliveryMethod delivery : DeliveryMethod.values()) assertThat(method.allowedWith(delivery)).isTrue();
        }
        assertThat(PaymentMethod.CASH_ON_DELIVERY.online()).isFalse();
        assertThat(PaymentMethod.parse("yape")).isEqualTo(PaymentMethod.YAPE);
    }

    // ---- Horario de la tienda ------------------------------------------------------------------------------

    @Test
    void easterAndHolyWeekHolidaysAreComputed() {
        assertThat(PeruHolidays.easterSunday(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(PeruHolidays.easterSunday(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(PeruHolidays.on(LocalDate.of(2026, 4, 2))).contains("Jueves Santo");
        assertThat(PeruHolidays.on(LocalDate.of(2026, 4, 3))).contains("Viernes Santo");
        assertThat(PeruHolidays.on(LocalDate.of(2026, 7, 28))).contains("Fiestas Patrias");
        assertThat(PeruHolidays.on(LocalDate.of(2026, 11, 13))).isEmpty();
    }

    @Test
    void storeOpensMondayToSaturdayAndClosesOnSundaysAndHolidays() {
        BusinessHours hours = BusinessHours.standard();
        LocalDateTime friday = LocalDateTime.of(2026, 11, 13, 10, 0); // viernes
        assertThat(hours.isOpen(friday)).isTrue();
        assertThat(hours.isOpen(friday.withHour(19))).isFalse(); // cierra a las 19:00
        assertThat(hours.isOpen(friday.withHour(8).withMinute(59))).isFalse();
        assertThat(hours.on(LocalDate.of(2026, 11, 15)).open()).isFalse(); // domingo
        BusinessHours.Day christmas = hours.on(LocalDate.of(2026, 12, 25));
        assertThat(christmas.open()).isFalse();
        assertThat(christmas.holiday()).isEqualTo("Navidad");
        // Sábado por la noche → abre el lunes a las 9:00.
        assertThat(hours.nextOpening(LocalDateTime.of(2026, 11, 14, 20, 0))).contains(LocalDateTime.of(2026, 11, 16, 9, 0));
    }
}
