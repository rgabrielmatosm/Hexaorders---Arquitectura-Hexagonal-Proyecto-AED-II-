package com.polymer.application.port.out;

import java.util.Optional;

/** Estado de seguridad de las cuentas: correo verificado, verificación en dos pasos e identidades externas. */
public interface AccountSecurity {
    SecurityState state(long userId);
    void setEmailVerified(long userId, boolean verified);
    void setTwoFactor(long userId, boolean enabled);
    Optional<Long> userForIdentity(String provider, String subject);
    void linkIdentity(long userId, String provider, String subject, String email);

    record SecurityState(boolean emailVerified, boolean twoFactor, boolean googleLinked) {}
}
