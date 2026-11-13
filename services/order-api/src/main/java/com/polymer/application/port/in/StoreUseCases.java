package com.polymer.application.port.in;

/** v7: información pública de la tienda física (mapa y horario de atención de hoy). */
public interface StoreUseCases {
    StoreView store();

    TodayView today();
}
