package com.polymer.domain.fulfillment;

/** roleDenied=true: the transition exists but the actor may not perform it (HTTP 403); otherwise 409. */
public final class TransitionNotAllowedException extends RuntimeException {
    private final boolean roleDenied;

    public TransitionNotAllowedException(String message, boolean roleDenied) {
        super(message);
        this.roleDenied = roleDenied;
    }

    public boolean roleDenied() { return roleDenied; }
}
