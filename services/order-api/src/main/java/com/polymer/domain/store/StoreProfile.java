package com.polymer.domain.store;

import java.util.Objects;

/**
 * Datos públicos de la tienda física (recojo y atención). La dirección es configurable
 * (POLYMER_STORE_*); el mapa se dibuja con la consulta de Google Maps de esa dirección.
 */
public record StoreProfile(String name, String address, String district, String city, String country, double latitude,
                           double longitude, String phone, String email) {
    public StoreProfile {
        Objects.requireNonNull(name, "name");
        if (address == null || address.isBlank()) throw new IllegalArgumentException("address: obligatoria");
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) throw new IllegalArgumentException("coordenadas no válidas");
    }

    /** Dirección completa para mostrar y para buscar en Google Maps. */
    public String fullAddress() {
        StringBuilder text = new StringBuilder(address);
        for (String part : new String[] {district, city, country}) {
            if (part != null && !part.isBlank()) text.append(", ").append(part);
        }
        return text.toString();
    }
}
