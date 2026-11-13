package com.polymer.application.port.in;

import java.util.List;

/** Tienda física para la interfaz: dirección, mapa (Google Maps), contacto y horario semanal. */
public record StoreView(String name, String address, String fullAddress, double latitude, double longitude, String phone, String email,
                        String mapsEmbedUrl, String mapsDirectionsUrl, String timeZone, List<WeekDay> week, String calendarEmbedUrl) {
    /** day: 1 = lunes … 7 = domingo; opens/closes "HH:mm" o null si cierra. */
    public record WeekDay(int day, String opens, String closes) {}
}
