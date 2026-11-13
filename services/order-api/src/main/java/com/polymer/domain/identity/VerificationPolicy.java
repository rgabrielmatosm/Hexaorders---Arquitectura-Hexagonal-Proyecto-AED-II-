package com.polymer.domain.identity;

import java.time.Duration;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * Reglas de la verificación por correo (código de un solo uso) y de los dispositivos recordados.
 * Son reglas de negocio puras: el núcleo decide cuánto vale un código y cuántos intentos admite;
 * los adaptadores solo lo guardan (hash) y lo envían.
 */
public final class VerificationPolicy {
    /** Dígitos del código enviado por correo. */
    public static final int CODE_DIGITS = 6;
    /** Vigencia del código. */
    public static final Duration CODE_TTL = Duration.ofMinutes(10);
    /** Intentos fallidos admitidos antes de invalidar el código. */
    public static final int MAX_ATTEMPTS = 5;
    /** Espera mínima entre dos envíos del mismo código (evita abusar del correo). */
    public static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    /** Envíos máximos por verificación. */
    public static final int MAX_SENDS = 5;
    /** «Recordarme»: sesión persistente y dispositivo de confianza. */
    public static final Duration REMEMBER_FOR = Duration.ofDays(30);

    private static final Pattern CODE = Pattern.compile("\\d{" + CODE_DIGITS + "}");

    private VerificationPolicy() {}

    /** Código aleatorio de 6 dígitos (con ceros a la izquierda): 000000–999999. */
    public static String newCode(RandomGenerator random) {
        int bound = (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", random.nextInt(bound));
    }

    /** Acepta espacios o guiones al teclear («123 456», «123-456»). */
    public static String normalize(String code) {
        return code == null ? "" : code.replaceAll("[\\s-]", "");
    }

    public static boolean wellFormed(String code) {
        return CODE.matcher(normalize(code)).matches();
    }

    /** Correo enmascarado para mostrarlo en pantalla sin revelarlo completo: «ca•••@gmail.com». */
    public static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "•••";
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "•••" + email.substring(at);
    }
}
