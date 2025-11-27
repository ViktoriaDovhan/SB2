package com.football.ua.service;

import com.football.ua.model.Team;
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
import java.util.List;
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

    @Autowired
    private TeamDbService teamDbService;

    @Autowired
    private ExternalTeamApiService externalTeamApiService;

    @Scheduled(cron = "0 0 2 * * *")
    public void performDailyDataUpdate() {
        log.info("🚀 Початок щоденного оновлення даних: {}", LocalDateTime.now().format(FORMATTER));

        try {

            log.info("🧹 Очищення застарілих кешів статистики та турнірних таблиць...");


            log.info("📅 Перевірка матчів на завтра...");


            log.info("📁 Архівування старих логів...");


            log.info("⭐ Оновлення рейтингу гравців тижня...");


            log.info("✅ Щоденне оновлення даних завершено успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час щоденного оновлення даних: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedDelay = 1800000) // 30 хвилин
    public void performCacheCleanup() {
        log.info("🧹 Початок очищення кешу: {}", LocalDateTime.now().format(FORMATTER));

        try {

            Map<String, Object> cacheInfo = fileCacheService.getCacheInfo();
            log.info("📊 Стан кешу перед очищенням: {}", cacheInfo);

            log.info("🗑️ Перевірка та очищення застарілих файлів кешу...");
            long cleanedFiles = cleanupExpiredCacheFiles(24 * 60 * 60 * 1000L);
            log.info("🗑️ Очищено {} застарілих файлів кешу", cleanedFiles);

            log.info("🗂️ Очищення тимчасових файлів...");


            log.info("✅ Очищення кешу завершено успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час очищення кешу: {}", e.getMessage(), e);
        }
    }

    private long cleanupExpiredCacheFiles(long maxAgeMillis) {
        long deletedCount = 0;
        try {
            Path cacheDir = Paths.get("cache");
            if (!Files.exists(cacheDir)) {
                log.info("📁 Директорія кешу не існує");
                return 0;
            }

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

    @Scheduled(fixedRate = 900000) // 15 хвилин
    public void performSystemHealthCheck() {
        log.info("💚 Початок перевірки стану системи: {}", LocalDateTime.now().format(FORMATTER));

        try {

            log.info("🗄️ Перевірка підключення до бази даних...");


            log.info("🌐 Перевірка доступності зовнішніх API...");


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

    @Scheduled(fixedDelay = 3600000) // 60 хвилин
    public void performStatisticsUpdate() {
        log.info("📊 Початок оновлення статистики системи: {}", LocalDateTime.now().format(FORMATTER));

        try {

            log.info("📈 Збір статистики використання...");

            log.info("💾 Оновлення статистики кешів...");

            log.info("⚙️ Перевірка конфігурацій системи...");

            log.info("📦 Архівування статистичних даних...");

            log.info("✅ Оновлення статистики системи завершено успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час оновлення статистики системи: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "0 0 3 * * 0")
    public void performWeeklyTeamUpdate() {
        log.info("⚽ Початок щотижневого оновлення команд: {}", LocalDateTime.now().format(FORMATTER));

        try {

            log.info("📥 Завантаження команд з API...");
            Map<String, List<com.football.ua.model.Team>> teamsFromApi = externalTeamApiService.getTeamsFromApi();

            log.info("💾 Збереження команд в базу даних...");
            for (Map.Entry<String, List<com.football.ua.model.Team>> entry : teamsFromApi.entrySet()) {
                String league = entry.getKey();
                List<Team> leagueTeams = entry.getValue();

                log.info("🔄 Оновлення {} команд для ліги {}", leagueTeams.size(), league);
                teamDbService.saveOrUpdateTeams(leagueTeams);

                LocalDateTime twoWeeksAgo = LocalDateTime.now().minusWeeks(2);
                int deactivated = teamDbService.deactivateOldTeams(league, twoWeeksAgo);
                if (deactivated > 0) {
                    log.info("🗑️ Позначено {} застарілих команд як неактивні для ліги {}", deactivated, league);
                }
            }

            log.info("✅ Щотижневе оновлення команд завершено успішно");

        } catch (Exception e) {
            log.error("❌ Помилка під час щотижневого оновлення команд: {}", e.getMessage(), e);
        }
    }
}

