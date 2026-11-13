package com.polymer.application.port.in;

import com.polymer.domain.identity.VerificationPurpose;
import java.time.Instant;
import java.util.UUID;

/** Lo que la interfaz muestra de una verificación pendiente (nunca el código). */
public record ChallengeView(UUID id, VerificationPurpose purpose, String maskedEmail, Instant expiresAt, int attemptsLeft,
                            long resendInSeconds, boolean canResend) {}
