package com.hexaorders.application.exception;

/** Raised by persistence when a concurrent request already stored an order with the same key. */
public final class IdempotencyKeyReusedException extends RuntimeException {
    public IdempotencyKeyReusedException(String key) { super("Idempotency-Key ya utilizada: " + key); }
}
