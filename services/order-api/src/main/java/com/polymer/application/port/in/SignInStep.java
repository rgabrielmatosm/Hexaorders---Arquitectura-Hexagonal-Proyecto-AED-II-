package com.polymer.application.port.in;

/** Resultado de un paso del inicio de sesión: completo (sin verificación pendiente) o a la espera del código. */
public record SignInStep(long userId, ChallengeView challenge, boolean newAccount) {
    public static SignInStep complete(long userId) { return new SignInStep(userId, null, false); }

    public boolean completed() { return challenge == null; }
}
