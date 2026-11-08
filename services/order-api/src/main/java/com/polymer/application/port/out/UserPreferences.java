package com.polymer.application.port.out;

import com.polymer.domain.identity.Language;

/** Preferencias de presentación de cada usuario (por ahora, el idioma de sus notificaciones). */
public interface UserPreferences {
    Language language(long userId);

    void setLanguage(long userId, Language language);
}
