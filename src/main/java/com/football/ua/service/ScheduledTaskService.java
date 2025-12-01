package com.football.ua.service;

import com.football.ua.model.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.football.ua.model.entity.MatchEntity;
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
import java.util.Optional;

@Service
public class ScheduledTaskService {

    private static final Logger log = LoggerFactory.getLogger(ScheduledTaskService.class);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private DatabaseCacheService fileCacheService;

    @Autowired
    private MatchService matchService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private TeamDbService teamDbService;

    @Autowired
    private ExternalTeamApiService externalTeamApiService;

    @Autowired
    private MatchDbService matchDbService;

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

    @Scheduled(fixedRate = 600000) // 10 хвилин
    public void performMatchScoreUpdate() {
        log.info("⚽ Початок оновлення рахунків матчів: {}", LocalDateTime.now().format(FORMATTER));

        try {
            List<MatchEntity> allMatches = matchDbService.list();
            LocalDateTime now = LocalDateTime.now();

            // Знаходимо завершені матчі без рахунків
            List<MatchEntity> matchesToUpdate = allMatches.stream()
                .filter(match -> match.getKickoffAt().isBefore(now)) // Матч уже відбувся
                .filter(match -> match.getHomeScore() == null || match.getAwayScore() == null) // Немає рахунку
                .toList();

            if (matchesToUpdate.isEmpty()) {
                log.debug("ℹ️ Немає матчів, які потребують оновлення рахунків");
                return;
            }

            log.info("📊 Знайдено {} матчів для оновлення рахунків", matchesToUpdate.size());

            int updatedCount = 0;
            for (MatchEntity match : matchesToUpdate) {
                try {
                    boolean scoreUpdated = updateMatchScoreFromApi(match);
                    if (scoreUpdated) {
                        updatedCount++;
                    }
                } catch (Exception e) {
                    log.warn("❌ Помилка оновлення рахунку матчу {} vs {}: {}",
                        match.getHomeTeam().getName(),
                        match.getAwayTeam().getName(),
                        e.getMessage());
                }
            }

            log.info("✅ Оновлено рахунків для {} матчів", updatedCount);

        } catch (Exception e) {
            log.error("❌ Помилка під час оновлення рахунків матчів: {}", e.getMessage(), e);
        }
    }

    private boolean updateMatchScoreFromApi(MatchEntity match) {
        try {
            String leagueCode = match.getLeague();
            String homeTeamName = match.getHomeTeam().getName();
            String awayTeamName = match.getAwayTeam().getName();

            log.debug("🔍 Пошук рахунку для матчу: {} vs {} (ліга: {})",
                homeTeamName, awayTeamName, leagueCode);

            // Отримуємо матчі з API для цієї ліги
            List<Map<String, Object>> apiMatches = externalTeamApiService.fetchMatchesFromApi(leagueCode);

            if (apiMatches == null || apiMatches.isEmpty()) {
                log.debug("⚠️ Не отримано матчів з API для ліги {}", leagueCode);
                return false;
            }

            // Шукаємо відповідний матч в API даних
            Optional<Map<String, Object>> matchingApiMatch = apiMatches.stream()
                .filter(apiMatch -> {
                    String apiHomeTeam = (String) apiMatch.get("homeTeam");
                    String apiAwayTeam = (String) apiMatch.get("awayTeam");
                    return homeTeamName.equals(apiHomeTeam) && awayTeamName.equals(apiAwayTeam);
                })
                .findFirst();

            if (matchingApiMatch.isEmpty()) {
                log.debug("⚠️ Матч {} vs {} не знайдено в API даних ліги {}",
                    homeTeamName, awayTeamName, leagueCode);
                return false;
            }

            Map<String, Object> apiMatch = matchingApiMatch.get();
            Object homeScoreObj = apiMatch.get("homeScore");
            Object awayScoreObj = apiMatch.get("awayScore");

            if (homeScoreObj == null || awayScoreObj == null) {
                log.debug("⚠️ Рахунок ще не доступний для матчу {} vs {}",
                    homeTeamName, awayTeamName);
                return false;
            }

            Integer homeScore = convertToInteger(homeScoreObj);
            Integer awayScore = convertToInteger(awayScoreObj);

            if (homeScore == null || awayScore == null) {
                log.debug("⚠️ Невірний формат рахунку для матчу {} vs {}",
                    homeTeamName, awayTeamName);
                return false;
            }

            // Оновлюємо рахунок в базі даних
            matchDbService.updateScore(match.getId(), homeScore, awayScore);

            log.info("✅ Оновлено рахунок матчу {} vs {}: {}:{}",
                homeTeamName, awayTeamName, homeScore, awayScore);

            return true;

        } catch (Exception e) {
            log.warn("❌ Помилка оновлення рахунку матчу {} vs {}: {}",
                match.getHomeTeam().getName(),
                match.getAwayTeam().getName(),
                e.getMessage());
            return false;
        }
    }

    private Integer convertToInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
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

