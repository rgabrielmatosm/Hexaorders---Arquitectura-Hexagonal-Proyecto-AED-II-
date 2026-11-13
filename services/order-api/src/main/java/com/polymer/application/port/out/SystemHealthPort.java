package com.polymer.application.port.out;

public interface SystemHealthPort {
    boolean databaseUp();
    boolean riskEngineUp();
}
