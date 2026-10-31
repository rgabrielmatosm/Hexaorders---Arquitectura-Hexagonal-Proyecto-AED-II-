package com.hexaorders.application.exception;

public final class RiskUnavailableException extends RuntimeException {
    public RiskUnavailableException(String message) { super(message); }
    public RiskUnavailableException(String message, Throwable cause) { super(message, cause); }
}
