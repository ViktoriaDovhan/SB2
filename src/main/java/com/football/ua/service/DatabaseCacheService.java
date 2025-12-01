package com.football.ua.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.football.ua.model.entity.ApiCacheEntity;
import com.football.ua.repo.ApiCacheRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DatabaseCacheService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseCacheService.class);

    private static final long TEAMS_CACHE_DURATION = 60;
    private static final long STANDINGS_CACHE_DURATION = 15;
    private static final long MATCHES_CACHE_DURATION = 60;

    @Autowired
    private ApiCacheRepository cacheRepository;

    private final ObjectMapper objectMapper;

    public DatabaseCacheService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        log.info("📁 Ініціалізовано DatabaseCacheService");
    }

    public <T> void saveToCache(String category, String key, T data) {
        saveToCache(category, key, data, getCacheDuration(category));
    }

    @Transactional
    public <T> void saveToCache(String category, String key, T data, long durationMinutes) {
        try {
            String jsonData = objectMapper.writeValueAsString(data);
            LocalDateTime timestamp = LocalDateTime.now();

            Optional<ApiCacheEntity> existing = cacheRepository.findByCategoryAndKey(category, key);
            
            ApiCacheEntity entity;
            if (existing.isPresent()) {
                entity = existing.get();
                entity.setData(jsonData);
                entity.setTimestamp(timestamp);
                entity.setDurationMinutes(durationMinutes);
            } else {
                entity = new ApiCacheEntity(category, key, jsonData, timestamp, durationMinutes);
            }
            
            cacheRepository.save(entity);
            log.info("💾 Збережено в кеш БД {}/{}: {} символів, тривалість: {} хв, timestamp: {}",
                     category, key, jsonData.length(), durationMinutes, timestamp);
        } catch (Exception e) {
            log.error("❌ Помилка збереження в кеш БД {}/{}: {}", category, key, e.getMessage());
        }
    }

    @Deprecated
    public <T> void saveToCache(String key, T data) {
        saveToCache("general", key, data);
    }

    @SuppressWarnings("unchecked")
    public <T> T loadFromCache(String category, String key, Class<T> clazz) {
        try {
            Optional<ApiCacheEntity> entityOpt = cacheRepository.findByCategoryAndKey(category, key);

            if (entityOpt.isEmpty()) {
                log.debug("📦 Кеш запис не існує: {}/{}", category, key);
                return null;
            }

            ApiCacheEntity entity = entityOpt.get();
            LocalDateTime now = LocalDateTime.now();
            long minutesOld = java.time.Duration.between(entity.getTimestamp(), now).toMinutes();

            if (minutesOld > entity.getDurationMinutes()) {
                log.debug("⏰ Кеш застарілий: {}/{} (вік: {} хв, ліміт: {} хв)",
                         category, key, minutesOld, entity.getDurationMinutes());
                return null;
            }

            T result = objectMapper.readValue(entity.getData(), clazz);
            log.info("✅ Завантажено з кешу БД: {}/{} (вік: {} хв, тип: {})", 
                    category, key, minutesOld, clazz.getSimpleName());
            return result;

        } catch (Exception e) {
            log.error("❌ Помилка читання з кешу БД {}/{}: {}", category, key, e.getMessage());
            return null;
        }
    }

    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> T loadFromCache(String key, Class<T> clazz) {
        return loadFromCache("general", key, clazz);
    }

    @SuppressWarnings("unchecked")
    public <T> T loadFromCacheIgnoringExpiration(String category, String key, Class<T> clazz) {
        try {
            Optional<ApiCacheEntity> entityOpt = cacheRepository.findByCategoryAndKey(category, key);

            if (entityOpt.isEmpty()) {
                log.debug("📦 Кеш запис не існує: {}/{}", category, key);
                return null;
            }

            ApiCacheEntity entity = entityOpt.get();
            LocalDateTime now = LocalDateTime.now();
            long minutesOld = java.time.Duration.between(entity.getTimestamp(), now).toMinutes();

            T result = objectMapper.readValue(entity.getData(), clazz);
            log.info("📦 Завантажено з кешу БД (ігноруючи термін дії): {}/{} (вік: {} хв, тип: {})", 
                    category, key, minutesOld, clazz.getSimpleName());
            return result;

        } catch (Exception e) {
            log.error("❌ Помилка читання з кешу БД {}/{}: {}", category, key, e.getMessage());
            return null;
        }
    }

    public boolean isCacheValid(String category, String key) {
        try {
            Optional<ApiCacheEntity> entityOpt = cacheRepository.findByCategoryAndKey(category, key);

            if (entityOpt.isEmpty()) {
                log.debug("📦 Кеш запис не існує: {}/{}", category, key);
                return false;
            }

            ApiCacheEntity entity = entityOpt.get();
            LocalDateTime now = LocalDateTime.now();
            long minutesOld = ChronoUnit.MINUTES.between(entity.getTimestamp(), now);

            log.debug("🔍 Деталі кешу {}/{}: різниця={} хв, ліміт={} хв",
                     category, key, minutesOld, entity.getDurationMinutes());

            if (minutesOld < 0) {
                log.warn("⚠️ Timestamp в майбутньому для {}/{}: {} < {} (різниця: {} хв)", 
                        category, key, entity.getTimestamp(), now, minutesOld);
                return false;
            }

            boolean isValid = minutesOld <= entity.getDurationMinutes();
            log.info("🔍 Перевірка кешу БД {}/{}: вік {} хв <= ліміт {} хв = {} ✅", 
                    category, key, minutesOld, entity.getDurationMinutes(), isValid);

            if (!isValid) {
                log.warn("⚠️ Кеш {}/{} застарілий: {} хв > {} хв", 
                        category, key, minutesOld, entity.getDurationMinutes());
            }

            return isValid;

        } catch (Exception e) {
            log.error("❌ Помилка перевірки кешу БД {}/{}: {}", category, key, e.getMessage());
            return false;
        }
    }

    @Deprecated
    public boolean isCacheValid(String key) {
        return isCacheValid("general", key);
    }

    @Transactional
    public void clearCache(String category, String key) {
        try {
            cacheRepository.deleteByCategoryAndKey(category, key);
            log.info("🗑️ Видалено кеш БД: {}/{}", category, key);
        } catch (Exception e) {
            log.error("❌ Помилка видалення кешу БД {}/{}: {}", category, key, e.getMessage());
        }
    }

    @Deprecated
    public void clearCache(String key) {
        clearCache("general", key);
    }

    @Transactional
    public void clearAllCache() {
        try {
            long count = cacheRepository.count();
            cacheRepository.deleteAll();
            log.info("🗑️ Очищено весь кеш БД: {} записів видалено", count);
        } catch (Exception e) {
            log.error("❌ Помилка очищення кешу БД: {}", e.getMessage());
        }
    }

    private long getCacheDuration(String category) {
        switch (category) {
            case "teams":
                return TEAMS_CACHE_DURATION;
            case "standings":
                return STANDINGS_CACHE_DURATION;
            case "matches":
                return MATCHES_CACHE_DURATION;
            case "players":
                return TEAMS_CACHE_DURATION;
            default:
                return 30;
        }
    }

    public Map<String, Object> getCacheInfo() {
        Map<String, Object> info = new HashMap<>();
        
        String[] categories = {"teams", "standings", "matches", "players", "general"};
        Map<String, Integer> categoryCounts = new HashMap<>();
        Map<String, Long> categorySizes = new HashMap<>();
        
        for (String category : categories) {
            long count = cacheRepository.countByCategory(category);
            List<ApiCacheEntity> entities = cacheRepository.findByCategory(category);
            long size = entities.stream()
                    .mapToLong(e -> e.getData() != null ? e.getData().length() : 0)
                    .sum();
            
            categoryCounts.put(category, (int) count);
            categorySizes.put(category, size);
        }
        
        info.put("totalCategories", categoryCounts.size());
        info.put("categoryCounts", categoryCounts);
        info.put("categorySizes", categorySizes);
        
        long totalSize = categorySizes.values().stream().mapToLong(Long::longValue).sum();
        info.put("totalSize", totalSize);
        
        return info;
    }

    public boolean shouldUpdateCache(String category, String key) {
        if (!isCacheValid(category, key)) {
            return true;
        }

        if ("matches".equals(category)) {
            return false;
        }

        return false;
    }
}
