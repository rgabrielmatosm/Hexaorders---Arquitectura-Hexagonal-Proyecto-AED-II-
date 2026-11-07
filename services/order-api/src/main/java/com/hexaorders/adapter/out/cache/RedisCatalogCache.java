package com.hexaorders.adapter.out.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hexaorders.application.port.out.CatalogCache;
import com.hexaorders.domain.catalog.Category;
import com.hexaorders.domain.catalog.Product;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis read-through cache of the PUBLIC catalog (categories, active products, product detail).
 *
 * - Keys embed a version number; invalidate() increments it, so every cached listing is replaced
 *   at once and old keys simply expire (TTL).
 * - Degradation: any Redis error is treated as a miss, PostgreSQL answers, and Redis is skipped for
 *   a short back-off period so a missing Redis never slows every request down.
 * - Errors of the loader (PostgreSQL) are NOT swallowed: they propagate as before.
 */
public final class RedisCatalogCache implements CatalogCache {
    private static final Logger LOG = LoggerFactory.getLogger(RedisCatalogCache.class);
    static final String VERSION_KEY = "hexaorders:catalog:version";
    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Duration backoff;
    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
    private final MeterRegistry registry;
    private volatile long skipUntil;
    private volatile boolean invalidationPending;

    public RedisCatalogCache(StringRedisTemplate redis, Duration ttl, Duration backoff, MeterRegistry registry) {
        this.redis = redis;
        this.ttl = ttl;
        this.backoff = backoff;
        this.registry = registry;
        for (String result : new String[] {"hit", "miss", "error", "bypass"}) counter(result);
    }

    private Counter counter(String result) {
        return Counter.builder("hexaorders_cache_requests").description("Lecturas de la caché Redis del catálogo por resultado")
                .tag("cache", "catalog").tag("result", result).register(registry);
    }

    @Override
    public List<Category> categories(Supplier<List<Category>> loader) {
        return cached("categories", json.getTypeFactory().constructCollectionType(List.class, Category.class), loader);
    }

    @Override
    public List<Product> activeProducts(Long categoryId, Supplier<List<Product>> loader) {
        return cached("products:" + (categoryId == null ? "all" : categoryId),
                json.getTypeFactory().constructCollectionType(List.class, Product.class), loader);
    }

    @Override
    public Optional<Product> product(long id, Supplier<Optional<Product>> loader) {
        Product value = cached("product:" + id, json.getTypeFactory().constructType(Product.class), () -> loader.get().orElse(null));
        return Optional.ofNullable(value);
    }

    @Override
    public void invalidate() {
        if (unavailable()) { invalidationPending = true; return; }
        try {
            redis.opsForValue().increment(VERSION_KEY);
            invalidationPending = false;
            Counter.builder("hexaorders_cache_invalidations").description("Invalidaciones de la caché del catálogo").register(registry).increment();
        } catch (RuntimeException error) {
            invalidationPending = true; // applied as soon as Redis answers again
            failed(error);
        }
    }

    private <T> T cached(String name, JavaType type, Supplier<T> loader) {
        String key = null;
        if (unavailable()) {
            counter("bypass").increment();
        } else {
            try {
                if (invalidationPending) { redis.opsForValue().increment(VERSION_KEY); invalidationPending = false; }
                String version = Optional.ofNullable(redis.opsForValue().get(VERSION_KEY)).orElse("0");
                key = "hexaorders:catalog6:v" + version + ":" + name;
                String raw = redis.opsForValue().get(key);
                if (raw != null) {
                    counter("hit").increment();
                    return json.readValue(raw, type);
                }
                counter("miss").increment();
            } catch (Exception error) {
                failed(error);
                key = null;
            }
        }
        T value = loader.get(); // PostgreSQL: the source of truth
        if (key != null && value != null) {
            try {
                redis.opsForValue().set(key, json.writeValueAsString(value), ttl);
            } catch (Exception error) {
                failed(error);
            }
        }
        return value;
    }

    private boolean unavailable() { return System.currentTimeMillis() < skipUntil; }

    private void failed(Exception error) {
        counter("error").increment();
        skipUntil = System.currentTimeMillis() + backoff.toMillis();
        LOG.warn("Redis no disponible ({}); se consulta PostgreSQL durante {} s", error.getClass().getSimpleName(), backoff.toSeconds());
    }
}
