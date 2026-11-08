package com.polymer.domain.identity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Política de contraseñas para clientes (registro y cambio). Prioriza la LONGITUD y la resistencia
 * frente a contraseñas conocidas, siguiendo NIST SP 800-63B: no exige combinaciones arbitrarias de
 * símbolos (las frases de contraseña con espacios son bienvenidas), pero rechaza las demasiado
 * cortas, las comunes, las que contienen el usuario o el correo y las repetitivas.
 *
 * <p>El límite superior viene de BCrypt (72 bytes): por encima, el algoritmo ignoraría el resto.
 * La misma lógica existe en el navegador (password-strength.js) solo como ayuda visual; la que
 * decide es esta.
 */
public final class PasswordPolicy {
    public static final int MIN_LENGTH = 12;
    public static final int MAX_LENGTH = 64;
    public static final int MAX_BYTES = 72;

    /** Problemas que hacen inaceptable una contraseña (códigos estables, traducibles en la interfaz). */
    public enum Problem { TOO_SHORT, TOO_LONG, COMMON, CONTAINS_PERSONAL_DATA, REPETITIVE }

    public enum Strength { WEAK, FAIR, STRONG }

    public record Assessment(Strength strength, List<Problem> problems) {
        public Assessment { problems = List.copyOf(problems); }
        public boolean acceptable() { return problems.isEmpty(); }
    }

    private PasswordPolicy() {}

    /**
     * @param personalData usuario, nombre o correo: no deben aparecer dentro de la contraseña.
     * @param isCommon     lista de contraseñas conocidas (normalizadas en minúsculas).
     */
    public static Assessment assess(String password, List<String> personalData, Predicate<String> isCommon) {
        String value = password == null ? "" : password;
        List<Problem> problems = new ArrayList<>();
        int length = value.codePointCount(0, value.length());
        if (length < MIN_LENGTH) problems.add(Problem.TOO_SHORT);
        if (length > MAX_LENGTH || value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) problems.add(Problem.TOO_LONG);
        String lower = value.toLowerCase(Locale.ROOT);
        String core = lower.replaceAll("^[^\\p{L}]+|[^\\p{L}]+$", "");    // «Password2026!» → «password»
        String compact = lower.replaceAll("[\\s._-]+", "");
        if (!lower.isEmpty() && (isCommon.test(lower) || isCommon.test(compact) || (core.length() >= 4 && isCommon.test(core)))) {
            problems.add(Problem.COMMON);
        }
        for (String data : personalData) {
            if (data == null) continue;
            for (String part : data.toLowerCase(Locale.ROOT).split("[@\\s._-]+")) {
                if (part.length() >= 4 && compact.contains(part)) { problems.add(Problem.CONTAINS_PERSONAL_DATA); break; }
            }
            if (problems.contains(Problem.CONTAINS_PERSONAL_DATA)) break;
        }
        if (length >= MIN_LENGTH && (distinct(compact) < 5 || isSequence(compact))) problems.add(Problem.REPETITIVE);
        if (!problems.isEmpty()) return new Assessment(Strength.WEAK, problems);
        return new Assessment(score(value, length) >= 24 ? Strength.STRONG : Strength.FAIR, problems);
    }

    /** Lanza IllegalArgumentException con el primer problema; el código viaja en {@link WeakPasswordException}. */
    public static void require(String password, List<String> personalData, Predicate<String> isCommon) {
        Assessment assessment = assess(password, personalData, isCommon);
        if (!assessment.acceptable()) throw new WeakPasswordException(assessment.problems());
    }

    static int score(String value, int length) {
        int classes = 0;
        if (value.chars().anyMatch(Character::isLowerCase)) classes++;
        if (value.chars().anyMatch(Character::isUpperCase)) classes++;
        if (value.chars().anyMatch(Character::isDigit)) classes++;
        if (value.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;
        int words = value.trim().split("[\\s._-]+").length;
        return Math.min(length, 40) + 4 * Math.max(0, classes - 1) + (words >= 3 ? 6 : 0);
    }

    private static long distinct(String value) { return value.codePoints().distinct().count(); }

    /** «123456789012», «abcdefghijkl», «987654321098»: cada carácter sigue al anterior. */
    private static boolean isSequence(String value) {
        if (value.length() < 4) return false;
        int up = 0;
        int down = 0;
        for (int i = 1; i < value.length(); i++) {
            int step = value.charAt(i) - value.charAt(i - 1);
            if (step == 1 || step == -9) up++;
            if (step == -1 || step == 9) down++;
        }
        return up >= value.length() - 2 || down >= value.length() - 2;
    }

    /** Contraseña rechazada por la política; los códigos permiten mostrar el motivo en cada idioma. */
    public static final class WeakPasswordException extends IllegalArgumentException {
        private final List<Problem> problems;

        public WeakPasswordException(List<Problem> problems) {
            super("password: " + describe(problems.get(0)));
            this.problems = List.copyOf(problems);
        }

        public List<Problem> problems() { return problems; }

        private static String describe(Problem problem) {
            return switch (problem) {
                case TOO_SHORT -> "usa al menos " + MIN_LENGTH + " caracteres; una frase de varias palabras es ideal";
                case TOO_LONG -> "usa como máximo " + MAX_LENGTH + " caracteres";
                case COMMON -> "es una contraseña muy común o conocida; elige otra";
                case CONTAINS_PERSONAL_DATA -> "no incluyas tu usuario, nombre ni correo";
                case REPETITIVE -> "evita repeticiones y secuencias como 123456 o abcdef";
            };
        }
    }
}
