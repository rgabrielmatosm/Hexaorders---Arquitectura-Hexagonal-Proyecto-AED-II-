package com.polymer.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj de pruebas que avanza a voluntad (vencimientos de 60 s y 10 min sin esperar). */
public final class MutableClock extends Clock {
    private Instant now;

    public MutableClock(Instant start) { this.now = start; }

    public void advance(Duration duration) { now = now.plus(duration); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }
    @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    @Override public Instant instant() { return now; }
}
