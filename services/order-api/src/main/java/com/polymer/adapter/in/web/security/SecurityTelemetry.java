package com.polymer.adapter.in.web.security;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/** Security metrics for the "Security & Audit" dashboard (no usernames as labels). */
public final class SecurityTelemetry {
    private final Counter loginSuccess;
    private final Counter loginFailure;
    private final Counter unauthenticated;
    private final Counter forbidden;
    private final MeterRegistry registry;

    public SecurityTelemetry(MeterRegistry registry) {
        this.registry = registry;
        for (String outcome : new String[] {"requested", "succeeded", "failed"}) verificationCounter(outcome);
        for (String method : new String[] {"password", "google", "verified", "remembered"}) signInCounter(method);
        loginSuccess = Counter.builder("polymer_auth_logins").tag("outcome", "success").description("Inicios de sesión").register(registry);
        loginFailure = Counter.builder("polymer_auth_logins").tag("outcome", "failure").description("Inicios de sesión").register(registry);
        unauthenticated = Counter.builder("polymer_access_denied").tag("reason", "unauthenticated").description("Accesos rechazados").register(registry);
        forbidden = Counter.builder("polymer_access_denied").tag("reason", "forbidden").description("Accesos rechazados").register(registry);
    }

    public void login(boolean success) { (success ? loginSuccess : loginFailure).increment(); }

    public void unauthenticated() { unauthenticated.increment(); }

    public void forbidden() { forbidden.increment(); }

    /** v7: códigos de verificación por correo (pedidos, correctos, incorrectos). */
    public void verificationRequested() { verificationCounter("requested").increment(); }

    public void verification(boolean success) { verificationCounter(success ? "succeeded" : "failed").increment(); }

    /** v7: sesiones abiertas por método: contraseña, Google o sesión recordada (30 días). */
    public void signedIn(String method) { signInCounter(method).increment(); }

    private Counter verificationCounter(String outcome) {
        return Counter.builder("polymer_auth_verifications").tag("outcome", outcome).description("Códigos de verificación por correo").register(registry);
    }

    private Counter signInCounter(String method) {
        return Counter.builder("polymer_auth_sessions").tag("method", method).description("Sesiones abiertas por método").register(registry);
    }
}
