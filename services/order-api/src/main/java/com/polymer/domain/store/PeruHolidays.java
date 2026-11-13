package com.polymer.domain.store;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Feriados nacionales del Perú (calendario civil vigente). Los de fecha fija y los móviles de Semana
 * Santa (Jueves y Viernes Santo, calculados con el algoritmo de Pascua de Gauss/Anonymous Gregorian).
 */
public final class PeruHolidays {
    private static final Map<MonthDay, String> FIXED = new LinkedHashMap<>();

    static {
        FIXED.put(MonthDay.of(1, 1), "Año Nuevo");
        FIXED.put(MonthDay.of(5, 1), "Día del Trabajo");
        FIXED.put(MonthDay.of(6, 7), "Batalla de Arica y Día de la Bandera");
        FIXED.put(MonthDay.of(6, 29), "San Pedro y San Pablo");
        FIXED.put(MonthDay.of(7, 23), "Día de la Fuerza Aérea del Perú");
        FIXED.put(MonthDay.of(7, 28), "Fiestas Patrias");
        FIXED.put(MonthDay.of(7, 29), "Fiestas Patrias");
        FIXED.put(MonthDay.of(8, 6), "Batalla de Junín");
        FIXED.put(MonthDay.of(8, 30), "Santa Rosa de Lima");
        FIXED.put(MonthDay.of(10, 8), "Combate de Angamos");
        FIXED.put(MonthDay.of(11, 1), "Día de Todos los Santos");
        FIXED.put(MonthDay.of(12, 8), "Inmaculada Concepción");
        FIXED.put(MonthDay.of(12, 9), "Batalla de Ayacucho");
        FIXED.put(MonthDay.of(12, 25), "Navidad");
    }

    private PeruHolidays() {}

    public static Optional<String> on(LocalDate date) {
        String fixed = FIXED.get(MonthDay.from(date));
        if (fixed != null) return Optional.of(fixed);
        LocalDate easter = easterSunday(date.getYear());
        if (date.equals(easter.minusDays(3))) return Optional.of("Jueves Santo");
        if (date.equals(easter.minusDays(2))) return Optional.of("Viernes Santo");
        return Optional.empty();
    }

    /** Domingo de Pascua (calendario gregoriano). */
    public static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
