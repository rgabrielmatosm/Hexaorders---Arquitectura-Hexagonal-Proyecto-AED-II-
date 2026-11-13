package com.polymer.application.port.out;

/** Contraseñas conocidas o filtradas (normalizadas en minúsculas) que no se aceptan. */
public interface PasswordBlocklist {
    boolean contains(String normalizedPassword);
}
