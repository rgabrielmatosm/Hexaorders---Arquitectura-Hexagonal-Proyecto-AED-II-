package com.polymer.application.port.out;

import java.time.Instant;
import java.util.Optional;

/**
 * Fichas de larga duración guardadas en el navegador como «serie.ficha»: la serie identifica la fila
 * y de la ficha solo se guarda su hash. Sesiones recordadas y dispositivos de confianza.
 */
public interface DeviceTokens {
    void store(Kind kind, String series, String tokenHash, long userId, Instant expiresAt);
    Optional<StoredToken> find(Kind kind, String series);
    void rotate(Kind kind, String series, String tokenHash, Instant expiresAt);
    void delete(Kind kind, String series);
    void deleteAllFor(Kind kind, long userId);

    enum Kind { REMEMBERED_LOGIN, TRUSTED_DEVICE }

    record StoredToken(String series, String tokenHash, long userId, Instant expiresAt) {}
}
