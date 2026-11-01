package com.hexaorders.adapter.out.persistence;

import com.hexaorders.application.exception.PersistenceUnavailableException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.function.Supplier;
import org.springframework.dao.DataAccessException;

/** Translates infrastructure errors into the application's PersistenceUnavailableException. */
final class Jdbc {
    private Jdbc() {}

    static <T> T run(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException error) {
            throw new PersistenceUnavailableException(error);
        }
    }

    static Timestamp ts(Instant instant) { return instant == null ? null : Timestamp.from(instant); }

    static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
