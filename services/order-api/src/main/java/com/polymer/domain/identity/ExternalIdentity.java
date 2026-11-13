package com.polymer.domain.identity;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/**
 * Identidad comprobada por un proveedor externo (Google). El sujeto («sub») es el identificador
 * estable; el correo puede cambiar. Solo se aceptan correos que el proveedor declara verificados.
 */
public record ExternalIdentity(String provider, String subject, String email, boolean emailVerified, String displayName) {
    public ExternalIdentity {
        if (provider == null || !provider.matches("[a-z]{2,16}")) throw new IllegalArgumentException("provider: no válido");
        if (subject == null || subject.isBlank() || subject.length() > 255) throw new IllegalArgumentException("subject: no válido");
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
        UserAccount.validateEmail(email);
        displayName = displayName == null || displayName.isBlank() ? email.substring(0, email.indexOf('@')) : displayName.strip();
        if (displayName.length() > 80) displayName = displayName.substring(0, 80);
        Objects.requireNonNull(provider);
    }

    /** Nombre de usuario sugerido a partir del correo: «Ana.Pérez@gmail.com» → «ana_perez». */
    public String suggestedUsername() {
        String local = email.substring(0, email.indexOf('@'));
        String ascii = Normalizer.normalize(local, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String clean = ascii.replaceAll("[^a-z0-9_-]", "_").replaceAll("_+", "_").replaceAll("^[_-]+|[_-]+$", "");
        if (clean.length() < 3) clean = (clean + "usuario").substring(0, Math.max(3, clean.length() + 7));
        return clean.length() > 24 ? clean.substring(0, 24) : clean;
    }
}
