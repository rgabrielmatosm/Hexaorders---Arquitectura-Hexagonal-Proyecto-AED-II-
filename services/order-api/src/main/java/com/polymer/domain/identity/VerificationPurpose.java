package com.polymer.domain.identity;

/** Por qué se pide un código: confirmar el correo de una cuenta nueva o un inicio de sesión. */
public enum VerificationPurpose {
    /** Cuenta registrada con contraseña cuyo correo aún no se ha confirmado. */
    EMAIL_VERIFICATION,
    /** Inicio de sesión con Google, o con verificación en dos pasos activada, desde un dispositivo no recordado. */
    SIGN_IN
}
