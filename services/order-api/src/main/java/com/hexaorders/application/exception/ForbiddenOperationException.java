package com.hexaorders.application.exception;

/** The authenticated user may not perform this operation (HTTP 403). */
public final class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) { super(message); }
}
