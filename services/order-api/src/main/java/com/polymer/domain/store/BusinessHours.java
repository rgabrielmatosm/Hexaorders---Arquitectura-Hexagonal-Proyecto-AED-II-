package com.polymer.domain.store;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Horario de atención de la tienda: semana tipo y feriados nacionales cerrados. Regla de negocio pura
 * (sin reloj del sistema): quien pregunta da la fecha y hora locales de Lima.
 */
public final class BusinessHours {
    private final Map<DayOfWeek, OpeningHours> week;
    private final boolean closedOnHolidays;

    public BusinessHours(Map<DayOfWeek, OpeningHours> week, boolean closedOnHolidays) {
        this.week = new EnumMap<>(DayOfWeek.class);
        this.week.putAll(week);
        this.closedOnHolidays = closedOnHolidays;
    }

    /** Lunes a sábado de 9:00 a 19:00; domingos y feriados cerrado. */
    public static BusinessHours standard() {
        Map<DayOfWeek, OpeningHours> week = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if (day != DayOfWeek.SUNDAY) week.put(day, OpeningHours.of("09:00", "19:00"));
        }
        return new BusinessHours(week, true);
    }

    public Map<DayOfWeek, OpeningHours> week() { return Map.copyOf(week); }

    /** Horario de una fecha concreta: vacío si cierra (domingo o feriado). */
    public Day on(LocalDate date) {
        Optional<String> holiday = PeruHolidays.on(date);
        if (closedOnHolidays && holiday.isPresent()) return new Day(date, null, holiday.get());
        return new Day(date, week.get(date.getDayOfWeek()), holiday.orElse(null));
    }

    public boolean isOpen(LocalDateTime now) {
        Day today = on(now.toLocalDate());
        return today.hours() != null && today.hours().contains(now.toLocalTime());
    }

    /** Próxima apertura a partir de este instante (hoy más tarde o en los próximos 14 días). */
    public Optional<LocalDateTime> nextOpening(LocalDateTime now) {
        for (int offset = 0; offset <= 14; offset++) {
            LocalDate date = now.toLocalDate().plusDays(offset);
            Day day = on(date);
            if (day.hours() == null) continue;
            LocalDateTime opens = LocalDateTime.of(date, day.hours().opens());
            if (opens.isAfter(now)) return Optional.of(opens);
        }
        return Optional.empty();
    }

    /** hours null = cerrado. holiday: nombre del feriado (si lo es, aunque abra). */
    public record Day(LocalDate date, OpeningHours hours, String holiday) {
        public boolean open() { return hours != null; }

        public Optional<LocalTime> closes() { return Optional.ofNullable(hours).map(OpeningHours::closes); }
    }
}
