package com.polymer.domain.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Una verificación pendiente: el hash del código enviado, su vencimiento y los intentos restantes.
 * El código en claro nunca se guarda.
 */
public record VerificationChallenge(UUID id, long userId, VerificationPurpose purpose, String codeHash, Instant expiresAt,
                                    int attemptsLeft, int sends, Instant lastSentAt, boolean consumed) {
    public VerificationChallenge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(purpose, "purpose");
        Objects.requireNonNull(codeHash, "codeHash");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(lastSentAt, "lastSentAt");
        if (attemptsLeft < 0 || attemptsLeft > VerificationPolicy.MAX_ATTEMPTS) throw new IllegalArgumentException("attemptsLeft fuera de rango");
        if (sends < 1) throw new IllegalArgumentException("sends: al menos un envío");
    }

    public static VerificationChallenge issue(UUID id, long userId, VerificationPurpose purpose, String codeHash, Instant now) {
        return new VerificationChallenge(id, userId, purpose, codeHash, now.plus(VerificationPolicy.CODE_TTL),
                VerificationPolicy.MAX_ATTEMPTS, 1, now, false);
    }

    public boolean expired(Instant now) { return !now.isBefore(expiresAt); }

    /** Utilizable: no consumida, no vencida y con intentos. */
    public boolean usable(Instant now) { return !consumed && !expired(now) && attemptsLeft > 0; }

    public Duration resendAvailableIn(Instant now) {
        Duration wait = Duration.between(now, lastSentAt.plus(VerificationPolicy.RESEND_INTERVAL));
        return wait.isNegative() ? Duration.ZERO : wait;
    }

    public boolean canResend(Instant now) {
        return !consumed && sends < VerificationPolicy.MAX_SENDS && resendAvailableIn(now).isZero();
    }

    public VerificationChallenge afterFailedAttempt() {
        return new VerificationChallenge(id, userId, purpose, codeHash, expiresAt, Math.max(0, attemptsLeft - 1), sends, lastSentAt, consumed);
    }

    /** Un código nuevo reinicia la vigencia y los intentos (el anterior deja de valer). */
    public VerificationChallenge resent(String newCodeHash, Instant now) {
        return new VerificationChallenge(id, userId, purpose, newCodeHash, now.plus(VerificationPolicy.CODE_TTL),
                VerificationPolicy.MAX_ATTEMPTS, sends + 1, now, false);
    }

    public VerificationChallenge consume() {
        return new VerificationChallenge(id, userId, purpose, codeHash, expiresAt, attemptsLeft, sends, lastSentAt, true);
    }
}
