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

public class CustomCacheManager implements CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CustomCacheManager.class);

    private final Map<String, Cache> cacheMap = new ConcurrentHashMap<>();

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

    private Cache createCache(String name) {
        CacheConfig config = CACHE_CONFIGS.getOrDefault(name,
            new CacheConfig(30, TimeUnit.MINUTES, 100L));

        com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(config.timeUnit.toMillis(config.duration)))
            .maximumSize(config.maxSize)
            .removalListener((key, value, cause) ->
                log.debug("Кеш '{}' - видалено елемент: {} через {}", name, key, cause))
            .recordStats()
            .build();

        log.info("Створено кеш '{}' з TTL={} {}, maxSize={}",
                name, config.duration, config.timeUnit, config.maxSize);

        return new CustomCaffeineCache(name, caffeineCache);
    }

    
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

    
    private static class CustomCaffeineCache extends CaffeineCache {

        private final com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache;

        public CustomCaffeineCache(String name, com.github.benmanes.caffeine.cache.Cache<Object, Object> caffeineCache) {
            super(name, caffeineCache);
            this.caffeineCache = caffeineCache;
        }

        
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

        
        public long clearAndCount() {
            long sizeBefore = caffeineCache.estimatedSize();
            caffeineCache.invalidateAll();
            return sizeBefore;
        }
    }

    
    public Map<String, Map<String, Object>> getAllCacheStats() {
        Map<String, Map<String, Object>> stats = new ConcurrentHashMap<>();
        for (Map.Entry<String, Cache> entry : cacheMap.entrySet()) {
            if (entry.getValue() instanceof CustomCaffeineCache customCache) {
                stats.put(entry.getKey(), customCache.getStats());
            }
        }
        return stats;
    }

    
    public Map<String, Long> clearAllCaches() {
        Map<String, Long> clearedCounts = new ConcurrentHashMap<>();

        for (String cacheName : cacheMap.keySet()) {
            Cache cache = cacheMap.get(cacheName);
            if (cache instanceof CustomCaffeineCache customCache) {
                long clearedCount = customCache.clearAndCount();
                clearedCounts.put(cacheName, clearedCount);

                cacheMap.remove(cacheName);
                
                log.info("Очищено та скинуто статистику кешу '{}': {} елементів", cacheName, clearedCount);
            }
        }
        return clearedCounts;
    }

    
    public long clearCache(String name) {
        Cache cache = cacheMap.get(name);
        if (cache instanceof CustomCaffeineCache customCache) {
            long clearedCount = customCache.clearAndCount();

            cacheMap.remove(name);
            
            log.info("Очищено та скинуто статистику кешу '{}': {} елементів", name, clearedCount);
            return clearedCount;
        }
        return 0;
    }
}

