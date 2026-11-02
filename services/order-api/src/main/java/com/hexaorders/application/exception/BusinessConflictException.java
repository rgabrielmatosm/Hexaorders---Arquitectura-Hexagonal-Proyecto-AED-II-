package com.hexaorders.application.exception;

/** A valid request that conflicts with the current state (HTTP 409). code is stable for clients. */
public class BusinessConflictException extends RuntimeException {
    private final String code;

    public BusinessConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}
