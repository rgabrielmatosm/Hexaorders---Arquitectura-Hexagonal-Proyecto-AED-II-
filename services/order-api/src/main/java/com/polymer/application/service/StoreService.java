package com.polymer.application.service;

import com.polymer.application.port.in.StoreUseCases;
import com.polymer.application.port.in.StoreView;
import com.polymer.application.port.in.TodayView;
import com.polymer.domain.store.BusinessHours;
import com.polymer.domain.store.OpeningHours;
import com.polymer.domain.store.StoreProfile;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Tienda física: mapa, horario semanal y «hoy» en la zona horaria de Lima. */
public final class StoreService implements StoreUseCases {
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter GCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private final StoreProfile profile;
    private final BusinessHours hours;
    private final Clock clock;
    private final ZoneId zone;
    private final String calendarId;

    public StoreService(StoreProfile profile, BusinessHours hours, Clock clock, ZoneId zone, String calendarId) {
        this.profile = profile;
        this.hours = hours;
        this.clock = clock;
        this.zone = zone;
        this.calendarId = calendarId == null ? "" : calendarId.strip();
    }

    @Override
    public StoreView store() {
        String coords = String.format(Locale.ROOT, "%.6f,%.6f", profile.latitude(), profile.longitude());
        List<StoreView.WeekDay> week = Arrays.stream(DayOfWeek.values()).map(day -> {
            OpeningHours slot = hours.week().get(day);
            return new StoreView.WeekDay(day.getValue(), slot == null ? null : HOUR.format(slot.opens()), slot == null ? null : HOUR.format(slot.closes()));
        }).toList();
        String calendar = calendarId.isEmpty() ? null
                : "https://calendar.google.com/calendar/embed?src=" + encode(calendarId) + "&ctz=" + encode(zone.getId()) + "&mode=AGENDA";
        return new StoreView(profile.name(), profile.address(), profile.fullAddress(), profile.latitude(), profile.longitude(),
                profile.phone(), profile.email(),
                "https://www.google.com/maps?q=" + coords + "&z=16&hl=es&output=embed",
                "https://www.google.com/maps/dir/?api=1&destination=" + coords,
                zone.getId(), week, calendar);
    }

    @Override
    public TodayView today() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(zone)).truncatedTo(ChronoUnit.MINUTES);
        BusinessHours.Day day = hours.on(now.toLocalDate());
        boolean open = hours.isOpen(now);
        String next = hours.nextOpening(now).map(LocalDateTime::toString).orElse(null);
        // Visita o recojo de 1 hora: ahora (si abre) o en la próxima apertura.
        LocalDateTime visit = open ? now.plusMinutes(30 - now.getMinute() % 30) : hours.nextOpening(now).orElse(now);
        if (open && day.hours() != null && !visit.toLocalTime().isBefore(day.hours().closes())) visit = hours.nextOpening(now).orElse(visit);
        String start = GCAL.format(visit.atZone(zone).withZoneSameInstant(ZoneOffset.UTC));
        String end = GCAL.format(visit.plusHours(1).atZone(zone).withZoneSameInstant(ZoneOffset.UTC));
        String calendarUrl = "https://calendar.google.com/calendar/render?action=TEMPLATE&text=" + encode("Visita a " + profile.name())
                + "&dates=" + start + "/" + end + "&ctz=" + encode(zone.getId())
                + "&location=" + encode(profile.name() + ", " + profile.fullAddress())
                + "&details=" + encode("Recojo de pedido o visita a la tienda " + profile.name() + ". Lleva tu número de pedido.");
        return new TodayView(now.toLocalDate().toString(), now.getDayOfWeek().getValue(), now.toLocalTime().toString(), open,
                day.hours() == null ? null : HOUR.format(day.hours().opens()), day.hours() == null ? null : HOUR.format(day.hours().closes()),
                day.holiday(), next, clock.instant(), calendarUrl);
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
