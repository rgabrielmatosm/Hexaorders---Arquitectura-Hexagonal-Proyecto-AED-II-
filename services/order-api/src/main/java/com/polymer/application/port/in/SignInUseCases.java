package com.polymer.application.port.in;

import com.polymer.domain.identity.ExternalIdentity;
import com.polymer.domain.identity.UserAccount;
import java.util.Optional;
import java.util.UUID;

/**
 * Inicio de sesión en dos pasos: el adaptador web comprueba el primer factor (contraseña o Google) y
 * el núcleo decide si hace falta el código de 6 dígitos enviado por correo. También gestiona
 * «Recordarme durante 30 días» (sesión persistente y dispositivo de confianza).
 */
public interface SignInUseCases {
    /** Primer factor superado con contraseña. trustedDevice: valor de la cookie del dispositivo (o null). */
    SignInStep afterPassword(long userId, String trustedDevice);

    /** Primer factor superado con Google: vincula o crea la cuenta y pide el código salvo en un dispositivo de confianza. */
    SignInStep withExternalIdentity(ExternalIdentity identity, String trustedDevice);

    /** Comprueba el código; si es correcto, la verificación queda consumida y el inicio de sesión completo. */
    SignInStep verify(UUID challengeId, String code);

    ChallengeView resend(UUID challengeId);

    ChallengeView challenge(UUID challengeId);

    /** false si no hay servidor de correo (modo basic): no se piden códigos. */
    boolean verificationEnabled();

    /** Marca este navegador como de confianza 30 días; devuelve el valor de la cookie. */
    String trustDevice(long userId);

    /** Sesión persistente de 30 días; devuelve el valor de la cookie. */
    String rememberLogin(long userId);

    /** Recupera una sesión recordada y rota la ficha (la anterior deja de valer). */
    Optional<ResumedLogin> resume(String rememberCookie);

    void forget(String rememberCookie);

    /** Cuenta activa para abrir la sesión (lanza si no existe o está desactivada). */
    UserAccount account(long userId);

    /** Cierra todas las sesiones recordadas y olvida todos los dispositivos de confianza del usuario. */
    void forgetAllDevices(AuthenticatedUser user);

    void setTwoFactor(AuthenticatedUser user, boolean enabled);

    SecuritySettings securitySettings(AuthenticatedUser user);

    record ResumedLogin(long userId, String rotatedCookie) {}

    record SecuritySettings(boolean emailVerified, boolean twoFactor, boolean googleLinked) {}
}
