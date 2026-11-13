package com.polymer.application.port.out;

import com.polymer.domain.identity.VerificationChallenge;
import java.util.Optional;
import java.util.UUID;

/** Verificaciones por correo pendientes (solo el hash del código). */
public interface VerificationChallenges {
    void save(VerificationChallenge challenge);
    Optional<VerificationChallenge> find(UUID id);
}
