package com.polymer.adapter.in.web.security;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

/**
 * Primer factor superado, a la espera del código (se guarda en la sesión HTTP, en el servidor).
 * Con esto no se tiene acceso a nada: la sesión no está autenticada hasta verificar el código.
 */
public record PendingSignIn(long userId, UUID challengeId, boolean remember, String next) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final String ATTRIBUTE = "polymer.pendingSignIn";
}
