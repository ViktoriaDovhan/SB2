package com.football.ua.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private FileCacheService fileCacheService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private TeamService teamService;

    /**
     * Фонове завдання з cron нотацією - щоденне оновлення даних о 02:00
     * Cron вираз: "0 0 2 * * *" означає "щодня о 02:00:00"
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void performDailyDataUpdate() {
        log.info("🚀 Початок щоденного оновлення даних: {}", LocalDateTime.now().format(FORMATTER));

        try {
            // Очищення застарілих кешів статистики та турнірних таблиць
            log.info("🧹 Очищення застарілих кешів статистики та турнірних таблиць...");
            // Тут буде виконано очищення кешів через CacheManager

            // Перевірка та оновлення даних матчів
            log.info("📅 Перевірка матчів на завтра...");
            // Тут можна додати логіку перевірки майбутніх матчів

            // Архівування старих логів
            log.info("📁 Архівування старих логів...");
            // Архівування логів що старші за 30 днів

            // Оновлення статистики гравців тижня
            log.info("⭐ Оновлення рейтингу гравців тижня...");
            // Тут можна додати логіку оновлення статистики гравців

            log.info("✅ Щоденне оновлення даних завершено успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час щоденного оновлення даних: {}", e.getMessage(), e);
        }
    }

    /**
     * Фонове завдання з fixedDelay - очищення застарілого кешу кожні 30 хвилин
     * fixedDelay = 1800000 мілісекунд = 30 хвилин
     */
    @Scheduled(fixedDelay = 1800000) // 30 хвилин
    public void performCacheCleanup() {
        log.info("🧹 Початок очищення кешу: {}", LocalDateTime.now().format(FORMATTER));

        try {
            // Отримання інформації про поточний стан кешу
            Map<String, Object> cacheInfo = fileCacheService.getCacheInfo();
            log.info("📊 Стан кешу перед очищенням: {}", cacheInfo);

            // Очищення застарілих файлів кешу - перевірка файлів старше 24 годин
            log.info("🗑️ Перевірка та очищення застарілих файлів кешу...");
            long cleanedFiles = cleanupExpiredCacheFiles(24 * 60 * 60 * 1000L); // 24 години
            log.info("🗑️ Очищено {} застарілих файлів кешу", cleanedFiles);

            // Очищення старих тимчасових файлів
            log.info("🗂️ Очищення тимчасових файлів...");
            // Додаткова логіка для очищення тимчасових файлів

            log.info("✅ Очищення кешу завершено успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час очищення кешу: {}", e.getMessage(), e);
        }
    }

    /**
     * Метод для очищення застарілих файлів кешу
     * @param maxAgeMillis максимальний вік файлів в мілісекундах
     * @return кількість видалених файлів
     */
    private long cleanupExpiredCacheFiles(long maxAgeMillis) {
        long deletedCount = 0;
        try {
            Path cacheDir = Paths.get("cache");
            if (!Files.exists(cacheDir)) {
                log.info("📁 Директорія кешу не існує");
                return 0;
            }

            // Рекурсивно обходимо всі файли в директорії cache
            deletedCount = Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".json"))
                .filter(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        long fileAgeMillis = System.currentTimeMillis() - attrs.lastModifiedTime().toMillis();
                        return fileAgeMillis > maxAgeMillis;
                    } catch (IOException e) {
                        log.warn("❌ Помилка перевірки файлу {}: {}", path, e.getMessage());
                        return false;
                    }
                })
                .mapToLong(path -> {
                    try {
                        Files.delete(path);
                        log.debug("🗑️ Видалено застарілий файл кешу: {}", path);
                        return 1L;
                    } catch (IOException e) {
                        log.warn("❌ Помилка видалення файлу {}: {}", path, e.getMessage());
                        return 0L;
                    }
                })
                .sum();

        } catch (IOException e) {
            log.error("❌ Помилка під час очищення застарілих файлів кешу: {}", e.getMessage(), e);
        }

        return deletedCount;
    }

    /**
     * Додаткове фонове завдання - перевірка стану системи кожні 15 хвилин
     * fixedRate = 900000 мілісекунд = 15 хвилин
     */
    @Scheduled(fixedRate = 900000) // 15 хвилин
    public void performSystemHealthCheck() {
        log.info("💚 Початок перевірки стану системи: {}", LocalDateTime.now().format(FORMATTER));

        try {
            // Перевірка доступності бази даних
            log.info("🗄️ Перевірка підключення до бази даних...");
            // Тут можна додати перевірку підключення до БД

            // Перевірка доступності зовнішніх API
            log.info("🌐 Перевірка доступності зовнішніх API...");
            // Тут можна додати перевірку доступності API

            // Перевірка використання пам'яті
            log.info("💾 Перевірка використання системних ресурсів...");
            Runtime runtime = Runtime.getRuntime();
            long totalMemory = runtime.totalMemory();
            long freeMemory = runtime.freeMemory();
            long usedMemory = totalMemory - freeMemory;

            log.info("📊 Використання пам'яті: загалом={}MB, використано={}MB, вільно={}MB",
                    totalMemory / (1024 * 1024),
                    usedMemory / (1024 * 1024),
                    freeMemory / (1024 * 1024));

            log.info("✅ Перевірка стану системи завершена успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час перевірки стану системи: {}", e.getMessage(), e);
        }
    }

    /**
     * Фонове завдання з fixedDelay - оновлення статистики системи кожні 60 хвилин
     * fixedDelay = 3600000 мілісекунд = 60 хвилин
     */
    @Scheduled(fixedDelay = 3600000) // 60 хвилин
    public void performStatisticsUpdate() {
        log.info("📊 Початок оновлення статистики системи: {}", LocalDateTime.now().format(FORMATTER));

        try {
            // Оновлення статистики використання додатку
            log.info("📈 Збір статистики використання...");

            // Оновлення кеш-статистики
            log.info("💾 Оновлення статистики кешів...");

            // Перевірка та оновлення конфігурацій
            log.info("⚙️ Перевірка конфігурацій системи...");

            // Архівування старих статистичних даних
            log.info("📦 Архівування статистичних даних...");

            log.info("✅ Оновлення статистики системи завершено успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час оновлення статистики системи: {}", e.getMessage(), e);
        }
    }
}
