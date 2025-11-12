package com.football.ua.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FileCacheService {
    private static final Logger log = LoggerFactory.getLogger(FileCacheService.class);
    private static final String CACHE_DIR = "cache";

    // Різні інтервали кешування для різних типів даних (у хвилинах)
    private static final long TEAMS_CACHE_DURATION = 60; // 1 година - команди змінюються рідко
    private static final long STANDINGS_CACHE_DURATION = 15; // 15 хвилин - турнірні таблиці оновлюються частіше
    private static final long MATCHES_CACHE_DURATION = 30; // 30 хвилин - матчі оновлюються помірно

    private final ObjectMapper objectMapper;

    public FileCacheService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Створюємо структуру директорій для кешу
        try {
            String[] subDirs = {"teams", "standings", "matches", "players"};
            for (String subDir : subDirs) {
                Path subPath = Paths.get(CACHE_DIR, subDir);
                if (!Files.exists(subPath)) {
                    Files.createDirectories(subPath);
                    log.info("📁 Створено піддиректорію кешу: {}/{}", CACHE_DIR, subDir);
                }
            }
            log.info("📁 Ініціалізовано структуру кешу");
        } catch (IOException e) {
            log.error("❌ Помилка створення структури кешу: {}", e.getMessage());
        }
    }

    // Загальний метод для збереження в кеш
    public <T> void saveToCache(String category, String key, T data) {
        saveToCache(category, key, data, getCacheDuration(category));
    }

    // Метод з вказанням тривалості кешування
    public <T> void saveToCache(String category, String key, T data, long durationMinutes) {
        try {
            Path categoryPath = Paths.get(CACHE_DIR, category);
            if (!Files.exists(categoryPath)) {
                Files.createDirectories(categoryPath);
            }

            LocalDateTime timestamp = LocalDateTime.now();
            Map<String, Object> cacheData = new HashMap<>();
            cacheData.put("timestamp", timestamp.toString());
            cacheData.put("duration", durationMinutes);
            cacheData.put("data", data);

            File cacheFile = new File(categoryPath.toFile(), key + ".json");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(cacheFile, cacheData);

            log.info("💾 Збережено в кеш {}/{}: {} байт, тривалість: {} хв, timestamp: {}",
                     category, key, cacheFile.length(), durationMinutes, timestamp);
        } catch (IOException e) {
            log.error("❌ Помилка збереження в кеш {}/{}: {}", category, key, e.getMessage());
        }
    }

    // Старий метод для зворотної сумісності
    @Deprecated
    public <T> void saveToCache(String key, T data) {
        saveToCache("general", key, data);
    }

    // Загальний метод для завантаження з кешу
    @SuppressWarnings("unchecked")
    public <T> T loadFromCache(String category, String key, Class<T> clazz) {
        try {
            File cacheFile = new File(Paths.get(CACHE_DIR, category, key + ".json").toString());

            if (!cacheFile.exists()) {
                log.debug("📦 Кеш файл не існує: {}/{}", category, key);
                return null;
            }

            Map<String, Object> cacheData = objectMapper.readValue(cacheFile, Map.class);
            String timestampStr = (String) cacheData.get("timestamp");
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr);

            // Отримуємо тривалість кешування (може бути Integer або Long)
            Object durationObj = cacheData.get("duration");
            Long durationMinutes;
            if (durationObj instanceof Long) {
                durationMinutes = (Long) durationObj;
            } else if (durationObj instanceof Integer) {
                durationMinutes = ((Integer) durationObj).longValue();
            } else if (durationObj instanceof String) {
                durationMinutes = Long.parseLong((String) durationObj);
            } else {
                durationMinutes = getCacheDuration(category);
            }

            // Перевіряємо чи не застарілий кеш
            LocalDateTime now = LocalDateTime.now();
            long minutesOld = java.time.Duration.between(timestamp, now).toMinutes();

            if (minutesOld > durationMinutes) {
                log.debug("⏰ Кеш застарілий: {}/{} (вік: {} хв, ліміт: {} хв)",
                         category, key, minutesOld, durationMinutes);
                return null;
            }

            Object data = cacheData.get("data");
            if (data == null) {
                log.warn("⚠️ Дані в кеші {}/{} порожні", category, key);
                return null;
            }

            T result = objectMapper.convertValue(data, clazz);
            log.info("✅ Завантажено з кешу: {}/{} (вік: {} хв, тип: {})", category, key, minutesOld, clazz.getSimpleName());
            return result;

        } catch (Exception e) {
            log.error("❌ Помилка читання з кешу {}/{}: {} (stacktrace: {})", category, key, e.getMessage(), e.getStackTrace());
            return null;
        }
    }

    // Старий метод для зворотної сумісності
    @Deprecated
    @SuppressWarnings("unchecked")
    public <T> T loadFromCache(String key, Class<T> clazz) {
        return loadFromCache("general", key, clazz);
    }

    // Метод для перевірки валідності кешу за категорією
    public boolean isCacheValid(String category, String key) {
        try {
            File cacheFile = new File(Paths.get(CACHE_DIR, category, key + ".json").toString());

            if (!cacheFile.exists()) {
                log.debug("📦 Кеш файл не існує: {}/{}", category, key);
                return false;
            }

            long fileSize = cacheFile.length();
            log.debug("📁 Кеш файл існує: {}/{} ({} байт)", category, key, fileSize);

            // Спочатку перевіряємо, чи файл не порожній
            if (fileSize == 0) {
                log.warn("⚠️ Кеш файл порожній: {}/{}", category, key);
                return false;
            }

            Map<String, Object> cacheData = objectMapper.readValue(cacheFile, Map.class);

            // Перевіряємо, чи є всі необхідні поля
            if (!cacheData.containsKey("timestamp") || !cacheData.containsKey("data")) {
                log.warn("⚠️ Кеш файл пошкоджений (відсутні поля): {}/{}", category, key);
                return false;
            }

            String timestampStr = (String) cacheData.get("timestamp");
            LocalDateTime timestamp = LocalDateTime.parse(timestampStr);

            // Отримуємо тривалість кешування (може бути Integer або Long)
            Object durationObj = cacheData.get("duration");
            Long durationMinutes;
            if (durationObj instanceof Long) {
                durationMinutes = (Long) durationObj;
            } else if (durationObj instanceof Integer) {
                durationMinutes = ((Integer) durationObj).longValue();
            } else if (durationObj instanceof String) {
                durationMinutes = Long.parseLong((String) durationObj);
            } else {
                durationMinutes = getCacheDuration(category);
            }

            LocalDateTime now = LocalDateTime.now();
            long minutesOld = ChronoUnit.MINUTES.between(timestamp, now);

            log.debug("🔍 Деталі кешу {}/{}: різниця={} хв, ліміт={} хв",
                     category, key, minutesOld, durationMinutes);

            // Захищаємося від негативних значень (якщо timestamp в майбутньому)
            if (minutesOld < 0) {
                log.warn("⚠️ Timestamp в майбутньому для {}/{}: {} < {} (різниця: {} хв)", category, key, timestamp, now, minutesOld);
                return false;
            }

            boolean isValid = minutesOld <= durationMinutes;
            log.info("🔍 Перевірка кешу {}/{}: вік {} хв <= ліміт {} хв = {} ✅", category, key, minutesOld, durationMinutes, isValid);

            if (!isValid) {
                log.warn("⚠️ Кеш {}/{} застарілий: {} хв > {} хв", category, key, minutesOld, durationMinutes);
            }

            return isValid;

        } catch (Exception e) {
            log.error("❌ Помилка перевірки кешу {}/{}: {}", category, key, e.getMessage());
            return false;
        }
    }

    // Старий метод для зворотної сумісності
    @Deprecated
    public boolean isCacheValid(String key) {
        return isCacheValid("general", key);
    }

    // Метод для очищення кешу за категорією
    public void clearCache(String category, String key) {
        try {
            File cacheFile = new File(Paths.get(CACHE_DIR, category, key + ".json").toString());
            if (cacheFile.exists()) {
                cacheFile.delete();
                log.info("🗑️ Видалено кеш: {}/{}", category, key);
            }
        } catch (Exception e) {
            log.error("❌ Помилка видалення кешу {}/{}: {}", category, key, e.getMessage());
        }
    }

    // Старий метод для зворотної сумісності
    @Deprecated
    public void clearCache(String key) {
        clearCache("general", key);
    }

    public void clearAllCache() {
        try {
            File cacheDir = new File(CACHE_DIR);
            if (cacheDir.exists() && cacheDir.isDirectory()) {
                clearDirectory(cacheDir);
                log.info("🗑️ Очищено весь кеш");
            }
        } catch (Exception e) {
            log.error("❌ Помилка очищення кешу: {}", e.getMessage());
        }
    }

    // Рекурсивне видалення директорії
    private void clearDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    clearDirectory(file);
                }
                file.delete();
            }
        }
    }

    // Метод для отримання тривалості кешування за категорією
    private long getCacheDuration(String category) {
        switch (category) {
            case "teams":
                return TEAMS_CACHE_DURATION;
            case "standings":
                return STANDINGS_CACHE_DURATION;
            case "matches":
                return MATCHES_CACHE_DURATION;
            case "players":
                return TEAMS_CACHE_DURATION; // гравці теж рідко змінюються
            default:
                return 30; // дефолт 30 хвилин
        }
    }

    // Метод для отримання інформації про кеш
    public Map<String, Object> getCacheInfo() {
        Map<String, Object> info = new HashMap<>();
        File cacheDir = new File(CACHE_DIR);

        if (cacheDir.exists() && cacheDir.isDirectory()) {
            Map<String, Integer> categoryCounts = new HashMap<>();
            Map<String, Long> categorySizes = new HashMap<>();

            String[] categories = {"teams", "standings", "matches", "players", "general"};
            for (String category : categories) {
                File categoryDir = new File(cacheDir, category);
                if (categoryDir.exists() && categoryDir.isDirectory()) {
                    File[] files = categoryDir.listFiles();
                    int count = files != null ? files.length : 0;
                    long size = 0;
                    if (files != null) {
                        for (File file : files) {
                            size += file.length();
                        }
                    }
                    categoryCounts.put(category, count);
                    categorySizes.put(category, size);
                }
            }

            info.put("totalCategories", categoryCounts.size());
            info.put("categoryCounts", categoryCounts);
            info.put("categorySizes", categorySizes);

            long totalSize = categorySizes.values().stream().mapToLong(Long::longValue).sum();
            info.put("totalSize", totalSize);
        }

        return info;
    }

    // Метод для перевірки чи потрібно оновити кеш (враховуючи час останнього оновлення)
    public boolean shouldUpdateCache(String category, String key) {
        if (!isCacheValid(category, key)) {
            return true;
        }

        // Додаткова логіка: наприклад, для матчів оновлювати частіше в ігрові дні
        if ("matches".equals(category)) {
            // Можна додати логіку перевірки часу дня, днів тижня тощо
            return false; // поки що не оновлюємо якщо валідний
        }

        return false;
    }
}


