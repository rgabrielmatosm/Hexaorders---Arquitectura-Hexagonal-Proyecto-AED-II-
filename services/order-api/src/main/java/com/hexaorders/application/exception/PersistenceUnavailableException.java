package com.hexaorders.application.exception;

public final class PersistenceUnavailableException extends RuntimeException {
    public PersistenceUnavailableException(Throwable cause) {
        super("La base de datos no está disponible", cause);
    }
}
