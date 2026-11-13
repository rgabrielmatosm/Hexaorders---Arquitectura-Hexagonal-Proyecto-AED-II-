package com.polymer.domain.identity;

import java.util.Arrays;
import java.util.Locale;

/** Idiomas de la interfaz y de las notificaciones. Agregar uno: añadirlo aquí y a la restricción de V10. */
public enum Language {
    ES, EN, FR, IT, PT, DE;

    public static final Language DEFAULT = ES;

    public String code() { return name().toLowerCase(Locale.ROOT); }

    public static Language fromCode(String code) {
        if (code == null) throw new IllegalArgumentException("language: es, en, fr, it, pt o de");
        return Arrays.stream(values()).filter(l -> l.code().equals(code.strip().toLowerCase(Locale.ROOT))).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("language: es, en, fr, it, pt o de"));
    }

    /** Valor guardado (o null) → idioma; desconocido o vacío → español. */
    public static Language orDefault(String code) {
        try { return code == null ? DEFAULT : fromCode(code); } catch (IllegalArgumentException unknown) { return DEFAULT; }
    }
}
