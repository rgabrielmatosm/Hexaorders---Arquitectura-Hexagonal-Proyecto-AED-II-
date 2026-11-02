package com.hexaorders.adapter.in.web.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Security metrics for the "Security & Audit" dashboard (no usernames as labels). */
public final class SecurityTelemetry {
    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter unauthenticated;
    private final Counter forbidden;

    public SecurityTelemetry(MeterRegistry registry) {
        loginSuccess = Counter.builder("hexaorders_auth_logins").tag("outcome", "success").description("Inicios de sesión").register(registry);
        loginFailure = Counter.builder("hexaorders_auth_logins").tag("outcome", "failure").description("Inicios de sesión").register(registry);
        unauthenticated = Counter.builder("hexaorders_access_denied").tag("reason", "unauthenticated").description("Accesos rechazados").register(registry);
        forbidden = Counter.builder("hexaorders_access_denied").tag("reason", "forbidden").description("Accesos rechazados").register(registry);
    }

    public void login(boolean success) { (success ? loginSuccess : loginFailure).increment(); }

    public void unauthenticated() { unauthenticated.increment(); }

    public void forbidden() { forbidden.increment(); }
}
