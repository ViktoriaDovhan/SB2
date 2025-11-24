package com.football.ua.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Власна реалізація CacheManager з використанням Caffeine
 * Підтримує різні конфігурації кешів для різних типів даних
 */
public class CustomCacheManager implements CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CustomCacheManager.class);

    private final Map<String, Cache> cacheMap = new ConcurrentHashMap<>();

    // Конфігурації для різних типів кешів
    private static final Map<String, CacheConfig> CACHE_CONFIGS = Map.of(
        "matches", new CacheConfig(30, TimeUnit.MINUTES, 1000L),
        "teams", new CacheConfig(60, TimeUnit.MINUTES, 500L),
        "standings", new CacheConfig(15, TimeUnit.MINUTES, 200L),
        "players", new CacheConfig(45, TimeUnit.MINUTES, 1000L),
        "statistics", new CacheConfig(10, TimeUnit.MINUTES, 500L),
        "predictions", new CacheConfig(5, TimeUnit.MINUTES, 200L)
    );

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return cacheMap.keySet();
    }

    /**
     * Створює кеш з відповідною конфігурацією
     */
    private Cache createCache(String name) {
        CacheConfig config = CACHE_CONFIGS.getOrDefault(name,
            new CacheConfig(30, TimeUnit.MINUTES, 100L)); // дефолтна конфігурація

        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(config.timeUnit.toMillis(config.duration)))
            .maximumSize(config.maxSize)
            .removalListener((key, value, cause) ->
                log.debug("Кеш '{}' - видалено елемент: {} через {}", name, key, cause))
            .recordStats() // для моніторингу статистики
            .build();

        log.info("Створено кеш '{}' з TTL={} {}, maxSize={}",
                name, config.duration, config.timeUnit, config.maxSize);

        return new CustomCaffeineCache(name, caffeineCache);
    }

    /**
     * Конфігурація для конкретного кешу
     */
    private static class CacheConfig {
        final long duration;
        final TimeUnit timeUnit;
        final long maxSize;

        CacheConfig(long duration, TimeUnit timeUnit, long maxSize) {
            this.duration = duration;
            this.timeUnit = timeUnit;
            this.maxSize = maxSize;
        }
    }

    /**
     * Розширена версія CaffeineCache з додатковою функціональністю
     */
    private static class CustomCaffeineCache extends CaffeineCache {

        private final com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache;

        public CustomCaffeineCache(String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache) {
            super(name, caffeineCache);
            this.caffeineCache = caffeineCache;
        }

        /**
         * Повертає статистику кешу
         */
        public Map<String, Object> getStats() {
            var stats = caffeineCache.stats();
            return Map.of(
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", stats.hitRate(),
                "evictionCount", stats.evictionCount(),
                "loadCount", stats.loadCount(),
                "size", caffeineCache.estimatedSize()
            );
        }

        /**
         * Очищає кеш і повертає кількість видалених елементів
         */
        public long clearAndCount() {
            long sizeBefore = caffeineCache.estimatedSize();
            caffeineCache.invalidateAll();
            return sizeBefore;
        }
    }

    /**
     * Повертає статистику для всіх кешів
     */
    public Map<String, Map<String, Object>> getAllCacheStats() {
        Map<String, Map<String, Object>> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, Cache> entry : cacheMap.entrySet()) {
            if (entry.getValue() instanceof CustomCaffeineCache customCache) {
                stats.put(entry.getKey(), customCache.getStats());
            }
        }
        return stats;
    }

    /**
     * Очищає всі кеші, скидає статистику і повертає статистику очищення
     */
    public Map<String, Long> clearAllCaches() {
        Map<String, Long> clearedCounts = new ConcurrentHashMap<>();
        
        // Створюємо копію ключів, щоб уникнути ConcurrentModificationException при видаленні
        for (String cacheName : cacheMap.keySet()) {
            Cache cache = cacheMap.get(cacheName);
            if (cache instanceof CustomCaffeineCache customCache) {
                long clearedCount = customCache.clearAndCount();
                clearedCounts.put(cacheName, clearedCount);
                
                // Видаляємо кеш з мапи, щоб при наступному зверненні він перестворився з новою статистикою
                cacheMap.remove(cacheName);
                
                log.info("Очищено та скинуто статистику кешу '{}': {} елементів", cacheName, clearedCount);
            }
        }
        return clearedCounts;
    }

    /**
     * Очищає конкретний кеш і скидає його статистику
     */
    public long clearCache(String name) {
        Cache cache = cacheMap.get(name);
        if (cache instanceof CustomCaffeineCache customCache) {
            long clearedCount = customCache.clearAndCount();
            
            // Видаляємо кеш з мапи, щоб при наступному зверненні він перестворився з новою статистикою
            cacheMap.remove(name);
            
            log.info("Очищено та скинуто статистику кешу '{}': {} елементів", name, clearedCount);
            return clearedCount;
        }
        return 0;
    }
}
