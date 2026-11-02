package com.hexaorders.application.exception;

public final class OutOfStockException extends BusinessConflictException {
    public OutOfStockException(String productName) {
        super("OUT_OF_STOCK", "Stock insuficiente para " + productName);
    }
}
