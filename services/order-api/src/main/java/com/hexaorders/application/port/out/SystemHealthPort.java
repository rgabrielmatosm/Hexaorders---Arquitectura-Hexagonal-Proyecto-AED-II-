package com.hexaorders.application.port.out;

public interface SystemHealthPort {
    boolean databaseUp();
    boolean riskEngineUp();
}
