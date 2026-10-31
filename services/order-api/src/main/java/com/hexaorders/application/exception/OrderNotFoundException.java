package com.hexaorders.application.exception;

import java.util.UUID;

public final class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(UUID id) {
        super("No existe el pedido " + id);
    }
}
