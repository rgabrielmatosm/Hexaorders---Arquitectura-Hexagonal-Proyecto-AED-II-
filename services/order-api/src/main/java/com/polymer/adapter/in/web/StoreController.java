package com.polymer.adapter.in.web;

import com.polymer.application.port.in.StoreUseCases;
import com.polymer.application.port.in.StoreView;
import com.polymer.application.port.in.TodayView;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v7 · Información pública de la tienda física: mapa, contacto y horario de atención de hoy. */
@RestController
@RequestMapping("/api/store")
public final class StoreController {
    private final StoreUseCases store;

    public StoreController(StoreUseCases store) { this.store = store; }

    @GetMapping
    public ResponseEntity<StoreView> store() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).body(store.store());
    }

    @GetMapping("/today")
    public ResponseEntity<TodayView> today() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()).body(store.today());
    }
}
