package com.polymer.application.exception;

/** No se pudo enviar un correo que el usuario necesita ahora (p. ej. el código de verificación). HTTP 503. */
public final class NotificationUnavailableException extends RuntimeException {
    public NotificationUnavailableException(String message, Throwable cause) { super(message, cause); }
}
