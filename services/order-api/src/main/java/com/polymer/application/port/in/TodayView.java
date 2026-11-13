package com.polymer.application.port.in;

import java.time.Instant;

/**
 * Hoy en la tienda (zona America/Lima): fecha, si está abierta ahora, horario de hoy, feriado y la
 * próxima apertura. Incluye el enlace «Agregar a Google Calendar» de una visita o recojo.
 */
public record TodayView(String date, int weekday, String now, boolean openNow, String opens, String closes, String holiday,
                        String nextOpening, Instant generatedAt, String googleCalendarUrl) {}
