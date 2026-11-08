package com.polymer.adapter.out.security;

import com.polymer.application.port.out.PasswordBlocklist;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Lista local de contraseñas comunes (static/security/common-passwords.txt). Es el mismo archivo que
 * usa el indicador del navegador, así que servidor e interfaz coinciden. No consulta servicios
 * externos: la tienda funciona sin Internet y nunca envía contraseñas fuera.
 */
public final class ClasspathPasswordBlocklist implements PasswordBlocklist {
    public static final String RESOURCE = "/static/security/common-passwords.txt";
    private final Set<String> entries;

    public ClasspathPasswordBlocklist() {
        Set<String> loaded = new HashSet<>();
        try (InputStream in = ClasspathPasswordBlocklist.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Falta " + RESOURCE);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                reader.lines().map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                        .map(line -> line.toLowerCase(Locale.ROOT)).forEach(loaded::add);
            }
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
        entries = Set.copyOf(loaded);
    }

    public int size() { return entries.size(); }

    @Override
    public boolean contains(String normalizedPassword) { return entries.contains(normalizedPassword); }
}
