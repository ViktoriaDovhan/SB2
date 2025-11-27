package com.football.ua.controller;

import com.football.ua.config.CustomCacheManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cache")
@Tag(name = "Cache Management", description = "API для управління кешами системи")
@PreAuthorize("hasRole('MODERATOR')")
public class CacheManagementController {

    private static final Logger log = LoggerFactory.getLogger(CacheManagementController.class);

    private final CacheManager cacheManager;

    public CacheManagementController(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @GetMapping("/stats")
    @Operation(summary = "Статистика кешів", description = "Повертає статистику використання всіх кешів")
    public ResponseEntity<Map<String, Map<String, Object>>> getCacheStats() {
        log.info("Запит на отримання статистики кешів");

        if (cacheManager instanceof CustomCacheManager customCacheManager) {
            Map<String, Map<String, Object>> stats = customCacheManager.getAllCacheStats();
            log.info("Повернуто статистику для {} кешів", stats.size());
            return ResponseEntity.ok(stats);
        }

        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/stats/{cacheName}")
    @Operation(summary = "Статистика конкретного кешу", description = "Повертає статистику використання вказаного кешу")
    public ResponseEntity<Map<String, Object>> getCacheStats(@PathVariable String cacheName) {
        log.info("Запит на отримання статистики кешу: {}", cacheName);

        if (cacheManager instanceof CustomCacheManager customCacheManager) {
            Map<String, Map<String, Object>> allStats = customCacheManager.getAllCacheStats();
            Map<String, Object> cacheStats = allStats.get(cacheName);

            if (cacheStats != null) {
                log.info("Повернуто статистику кешу '{}': hitRate={}, size={}",
                        cacheName, cacheStats.get("hitRate"), cacheStats.get("size"));
                return ResponseEntity.ok(cacheStats);
            } else {
                log.warn("Кеш '{}' не знайдено", cacheName);
                return ResponseEntity.notFound().build();
            }
        }

        return ResponseEntity.badRequest().build();
    }

    @DeleteMapping("/clear")
    @Operation(summary = "Очистити всі кеші", description = "Видаляє всі елементи з усіх кешів системи")
    public ResponseEntity<Map<String, Long>> clearAllCaches() {
        log.info("Запит на очищення всіх кешів");

        if (cacheManager instanceof CustomCacheManager customCacheManager) {
            Map<String, Long> clearedCounts = customCacheManager.clearAllCaches();
            long totalCleared = clearedCounts.values().stream().mapToLong(Long::longValue).sum();

            log.info("Очищено всі кеші: загалом {} елементів видалено", totalCleared);
            return ResponseEntity.ok(clearedCounts);
        }

        return ResponseEntity.badRequest().build();
    }

    @DeleteMapping("/clear/{cacheName}")
    @Operation(summary = "Очистити конкретний кеш", description = "Видаляє всі елементи з вказаного кешу")
    public ResponseEntity<Map<String, Object>> clearCache(@PathVariable String cacheName) {
        log.info("Запит на очищення кешу: {}", cacheName);

        if (cacheManager instanceof CustomCacheManager customCacheManager) {
            long clearedCount = customCacheManager.clearCache(cacheName);

            Map<String, Object> response = Map.of(
                "cacheName", cacheName,
                "clearedElements", clearedCount,
                "message", "Кеш успішно очищено"
            );

            log.info("Очищено кеш '{}': {} елементів видалено", cacheName, clearedCount);
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.badRequest().build();
    }

    @GetMapping("/info")
    @Operation(summary = "Інформація про кеші", description = "Повертає інформацію про всі доступні кеші та їх конфігурації")
    public ResponseEntity<Map<String, Object>> getCacheInfo() {
        log.info("Запит на отримання інформації про кеші");

        Map<String, Object> info = Map.of(
            "cacheNames", cacheManager.getCacheNames(),
            "cacheManagerType", cacheManager.getClass().getSimpleName(),
            "totalCaches", cacheManager.getCacheNames().size()
        );

        log.info("Повернуто інформацію про {} кешів", cacheManager.getCacheNames().size());
        return ResponseEntity.ok(info);
    }

    @GetMapping("/exists/{cacheName}")
    @Operation(summary = "Перевірити існування кешу", description = "Перевіряє чи існує вказаний кеш")
    public ResponseEntity<Map<String, Object>> cacheExists(@PathVariable String cacheName) {
        boolean exists = cacheManager.getCache(cacheName) != null;

        Map<String, Object> response = Map.of(
            "cacheName", cacheName,
            "exists", exists
        );

        log.debug("Перевірка існування кешу '{}': {}", cacheName, exists);
        return ResponseEntity.ok(response);
    }
}
