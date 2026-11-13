package com.polymer.domain.store;

import java.time.LocalTime;

/** Franja de atención de un día (apertura < cierre, mismo día). */
public record OpeningHours(LocalTime opens, LocalTime closes) {
    public OpeningHours {
        if (opens == null || closes == null || !opens.isBefore(closes)) throw new IllegalArgumentException("Horario no válido");
    }

    public boolean contains(LocalTime time) { return !time.isBefore(opens) && time.isBefore(closes); }

    public static OpeningHours of(String opens, String closes) { return new OpeningHours(LocalTime.parse(opens), LocalTime.parse(closes)); }
}
