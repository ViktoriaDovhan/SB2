package com.football.ua.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.football.ua.model.Team;
import com.football.ua.model.entity.MatchEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

    @Autowired
    private TeamDbService teamDbService;

    @Autowired
    private DatabaseCacheService fileCacheService;

    @Autowired
    private MatchDbService matchDbService;

    @Autowired
    private ExternalTeamApiService externalTeamApiService;

    @Autowired
    private ScorerDbService scorerDbService;

    @Autowired
    private StandingDbService standingDbService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== МІГРАЦІЯ КОМАНД ====================

    public void migrateTeamsFromCacheToDatabase() {
        log.info("🔄 Початок міграції команд з кешу в базу даних");

        try {
            File allTeamsFile = new File("cache/teams/all_teams.json");
            if (!allTeamsFile.exists()) {
                log.warn("⚠️ Файл cache/teams/all_teams.json не знайдено, пропускаємо міграцію");
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> cacheData = objectMapper.readValue(allTeamsFile, Map.class);
            @SuppressWarnings("unchecked")
            Map<String, List<Map<String, Object>>> teamsData = (Map<String, List<Map<String, Object>>>) cacheData.get("data");

            if (teamsData == null || teamsData.isEmpty()) {
                log.warn("⚠️ Дані команд не знайдені в кеші");
                return;
            }

            List<Team> allTeams = new ArrayList<>();

            for (Map.Entry<String, List<Map<String, Object>>> entry : teamsData.entrySet()) {
                String league = entry.getKey();
                List<Map<String, Object>> leagueTeams = entry.getValue();

                log.info("Обробка ліги {}: {} команд", league, leagueTeams.size());

                for (Map<String, Object> teamData : leagueTeams) {
                    try {
                        Team team = convertMapToTeam(teamData, league);
                        if (team != null) {
                            allTeams.add(team);
                        }
                    } catch (Exception e) {
                        log.warn("❌ Помилка конвертації команди: {}", e.getMessage());
                    }
                }
            }

            if (!allTeams.isEmpty()) {
                teamDbService.saveOrUpdateTeams(allTeams);
                log.info("✅ Міграція завершена: збережено {} команд в базу даних", allTeams.size());
            } else {
                log.warn("⚠️ Не знайдено команд для міграції");
            }

        } catch (Exception e) {
            log.error("❌ Критична помилка під час міграції команд: {}", e.getMessage(), e);
        }
    }

    private Team convertMapToTeam(Map<String, Object> teamData, String league) {
        try {
            Team team = new Team();

            Object idObj = teamData.get("id");
            if (idObj instanceof Number) {
                team.id = ((Number) idObj).longValue();
            } else if (idObj instanceof String) {
                try {
                    team.id = Long.parseLong((String) idObj);
                } catch (NumberFormatException e) {
                    log.warn("Невірний формат ID: {}", idObj);
                    return null;
                }
            }

            team.name = (String) teamData.get("name");
            team.league = league;
            team.city = (String) teamData.get("city");
            team.colors = (String) teamData.get("colors");
            team.emblemUrl = (String) teamData.get("emblemUrl");

            if (team.name == null || team.name.trim().isEmpty()) {
                log.warn("Команда без імені пропущена");
                return null;
            }

            return team;

        } catch (Exception e) {
            log.warn("Помилка конвертації команди: {}", e.getMessage());
            return null;
        }
    }

    public boolean isDatabaseEmpty() {
        try {
            Map<String, List<Team>> teams = teamDbService.getAllTeams();
            return teams.isEmpty() || teams.values().stream().allMatch(List::isEmpty);
        } catch (Exception e) {
            log.warn("❌ Помилка перевірки бази даних: {}", e.getMessage());
            return true;
        }
    }

    public void cleanupTeamCacheFiles() {
        log.info("🗑️ Початок очищення кеш файлів команд");

        try {
            File teamsCacheDir = new File("cache/teams");
            if (!teamsCacheDir.exists()) {
                log.info("📁 Директорія cache/teams не існує");
                return;
            }

            File[] cacheFiles = teamsCacheDir.listFiles((dir, name) -> name.endsWith(".json"));
            if (cacheFiles == null || cacheFiles.length == 0) {
                log.info("📁 Кеш файли команд не знайдені");
                return;
            }

            int deletedCount = 0;
            for (File file : cacheFiles) {
                try {
                    if (file.delete()) {
                        log.debug("🗑️ Видалено: {}", file.getName());
                        deletedCount++;
                    } else {
                        log.warn("❌ Не вдалося видалити: {}", file.getName());
                    }
                } catch (Exception e) {
                    log.warn("❌ Помилка видалення файлу {}: {}", file.getName(), e.getMessage());
                }
            }

            log.info("✅ Очищення завершено: видалено {} файлів", deletedCount);

        } catch (Exception e) {
            log.error("❌ Помилка під час очищення кеш файлів: {}", e.getMessage(), e);
        }
    }

    // ==================== МІГРАЦІЯ МАТЧІВ ====================

    /**
     * Викликається з DataInitializer.
     * Якщо матчі в БД вже є – нічого не робимо.
     * Якщо немає – тягнемо їх з API і зберігаємо через MatchDbService.create(...)
     */
    public void migrateMatchesFromCacheToDatabase() {
        log.info("🔄 Перевірка наявності матчів в БД перед міграцією з API");

        try {
            List<MatchEntity> existing = matchDbService.list();
            if (existing != null && !existing.isEmpty()) {
                log.info("✅ В БД вже є {} матчів, міграцію пропускаємо", existing.size());
                return;
            }

            log.info("ℹ️ Матчі в БД відсутні, виконуємо першу міграцію з API");
            migrateMatchesForAllLeagues();

        } catch (Exception e) {
            log.error("❌ Критична помилка під час перевірки/міграції матчів: {}", e.getMessage(), e);
        }
    }

    public boolean hasMatches() {
        try {
            return fileCacheService.isCacheValid("matches", "all_matches") ||
                    fileCacheService.isCacheValid("matches", "upcoming_matches_by_matchday") ||
                    fileCacheService.isCacheValid("matches", "previous_matches_by_matchday");
        } catch (Exception e) {
            log.warn("❌ Помилка перевірки наявності матчів: {}", e.getMessage());
            return false;
        }
    }

    public Map<String, Integer> migrateMatchesForAllLeagues() {
        log.info("🔄 Початок міграції матчів з API для всіх ліг");
        Map<String, Integer> results = new java.util.LinkedHashMap<>();

        List<String> leagues = java.util.Arrays.asList("UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");

        for (String league : leagues) {
            try {
                int count = migrateMatchesForLeague(league);
                results.put(league, count);
                log.info("✅ Міграція матчів для {}: {} матчів", league, count);
            } catch (Exception e) {
                log.error("❌ Помилка міграції матчів для {}: {}", league, e.getMessage());
                results.put(league, 0);
            }
        }

        int total = results.values().stream().mapToInt(Integer::intValue).sum();
        log.info("✅ Міграція матчів завершена: {} матчів для {} ліг", total, leagues.size());

        return results;
    }

    /**
     * Завантажує матчі з API і створює їх через MatchDbService.create(...)
     * НІЯКИХ додаткових методів у MatchDbService не потрібно.
     */
    private int migrateMatchesForLeague(String leagueCode) {
        log.info("📥 Завантаження матчів для ліги: {}", leagueCode);

        try {
            List<Map<String, Object>> matchesData = externalTeamApiService.fetchMatchesFromApi(leagueCode);

            if (matchesData == null || matchesData.isEmpty()) {
                log.warn("⚠️ Отримано 0 матчів для ліги {}", leagueCode);
                return 0;
            }

            int created = 0;

            for (Map<String, Object> matchData : matchesData) {
                try {
                    // Підтримуємо і старі, і нові ключі
                    String homeTeamName = (String) (
                            matchData.get("homeTeamName") != null
                                    ? matchData.get("homeTeamName")
                                    : matchData.get("homeTeam")
                    );

                    String awayTeamName = (String) (
                            matchData.get("awayTeamName") != null
                                    ? matchData.get("awayTeamName")
                                    : matchData.get("awayTeam")
                    );

                    Object kickoffRaw = matchData.get("kickoffAt");
                    LocalDateTime kickoffAt = toLocalDateTime(kickoffRaw);

                    if (homeTeamName == null || awayTeamName == null || kickoffAt == null) {
                        log.warn("⚠️ Пропускаємо матч з некоректними даними: home={}, away={}, kickoff={}",
                                homeTeamName, awayTeamName, kickoffRaw);
                        continue;
                    }

                    matchDbService.create(homeTeamName, awayTeamName, kickoffAt, leagueCode);
                    created++;
                } catch (Exception e) {
                    log.warn("❌ Помилка обробки одного матчу для {}: {}", leagueCode, e.getMessage());
                }
            }

            log.info("📦 Для ліги {} створено {} матчів у БД", leagueCode, created);
            return created;

        } catch (Exception e) {
            log.error("❌ Помилка завантаження матчів для {}: {}", leagueCode, e.getMessage());
            return 0;
        }
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.isEmpty()) {
                return null;
            }
            try {
                // якщо є часовий пояс
                if (text.endsWith("Z") || text.contains("+")) {
                    return java.time.OffsetDateTime.parse(text).toLocalDateTime();
                }
                return LocalDateTime.parse(text);
            } catch (Exception e) {
                log.warn("❌ Не вдалося розпарсити дату '{}': {}", text, e.getMessage());
                return null;
            }
        }
        return null;
    }

    // ==================== МІГРАЦІЯ ТАБЛИЦЬ ====================

    public Map<String, Integer> migrateStandingsForAllLeagues() {
        log.info("🔄 Початок міграції турнірних таблиць з API для всіх ліг");
        Map<String, Integer> results = new java.util.LinkedHashMap<>();

        List<String> leagues = java.util.Arrays.asList("UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");

        for (String league : leagues) {
            try {
                Map<String, Object> standings = externalTeamApiService.getLeagueStandings(league);
                if (standings != null && standings.containsKey("standings")) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> standingsData = (List<Map<String, Object>>) standings.get("standings");

                    if (standingsData != null && !standingsData.isEmpty()) {
                        List<com.football.ua.model.entity.StandingEntity> entities = new ArrayList<>();

                        for (Map<String, Object> standing : standingsData) {
                            try {
                                com.football.ua.model.entity.StandingEntity entity = new com.football.ua.model.entity.StandingEntity();
                                entity.setPosition(getInteger(standing.get("position")));
                                entity.setTeamName((String) standing.get("teamName"));
                                entity.setTeamCrest((String) standing.get("teamCrest"));
                                entity.setPlayedGames(getInteger(standing.get("playedGames")));
                                entity.setWon(getInteger(standing.get("won")));
                                entity.setDraw(getInteger(standing.get("draw")));
                                entity.setLost(getInteger(standing.get("lost")));
                                entity.setGoalsFor(getInteger(standing.get("goalsFor")));
                                entity.setGoalsAgainst(getInteger(standing.get("goalsAgainst")));
                                entity.setGoalDifference(getInteger(standing.get("goalDifference")));
                                entity.setPoints(getInteger(standing.get("points")));
                                entity.setLeague(league);
                                entity.setLastUpdated(java.time.LocalDateTime.now());

                                entities.add(entity);
                            } catch (Exception e) {
                                log.warn("❌ Помилка конвертації запису таблиці для {}: {}", league, e.getMessage());
                            }
                        }

                        if (!entities.isEmpty()) {
                            standingDbService.deleteByLeague(league);
                            standingDbService.saveAll(entities);
                            results.put(league, entities.size());
                            log.info("✅ Міграція таблиці для {}: {} позицій збережено в БД", league, entities.size());
                        } else {
                            results.put(league, 0);
                        }
                    } else {
                        results.put(league, 0);
                    }
                } else {
                    results.put(league, 0);
                }
            } catch (Exception e) {
                log.error("❌ Помилка міграції таблиці для {}: {}", league, e.getMessage());
                results.put(league, 0);
            }
        }

        int total = results.values().stream().mapToInt(Integer::intValue).sum();
        log.info("✅ Міграція таблиць завершена: {} позицій для {} ліг збережено в БД", total, leagues.size());

        return results;
    }

    // ==================== МІГРАЦІЯ БОМБАРДИРІВ ====================

    public Map<String, Integer> migrateScorersForAllLeagues() {
        log.info("🔄 Початок міграції бомбардирів з API для всіх ліг");
        Map<String, Integer> results = new java.util.LinkedHashMap<>();

        List<String> leagues = java.util.Arrays.asList("UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");

        for (String league : leagues) {
            try {
                List<Map<String, Object>> scorers = externalTeamApiService.fetchScorersFromApi(league);

                if (scorers != null && !scorers.isEmpty()) {
                    List<com.football.ua.model.entity.ScorerEntity> entities = new ArrayList<>();

                    for (Map<String, Object> scorer : scorers) {
                        try {
                            com.football.ua.model.entity.ScorerEntity entity = new com.football.ua.model.entity.ScorerEntity();
                            entity.setPlayerId(getInteger(scorer.get("playerId")));
                            entity.setPlayerName((String) scorer.get("playerName"));
                            entity.setTeamId(getInteger(scorer.get("teamId")));
                            entity.setTeamName((String) scorer.get("teamName"));
                            entity.setGoals(getInteger(scorer.get("goals")));
                            entity.setAssists(getInteger(scorer.get("assists")));
                            entity.setPenalties(getInteger(scorer.get("penalties")));
                            entity.setLeague(league);
                            entity.setLastUpdated(java.time.LocalDateTime.now());

                            entities.add(entity);
                        } catch (Exception e) {
                            log.warn("❌ Помилка конвертації бомбардира для {}: {}", league, e.getMessage());
                        }
                    }

                    if (!entities.isEmpty()) {
                        scorerDbService.deleteByLeague(league);
                        scorerDbService.saveAll(entities);
                        results.put(league, entities.size());
                        log.info("✅ Міграція бомбардирів для {}: {} гравців збережено в БД", league, entities.size());
                    } else {
                        results.put(league, 0);
                    }
                } else {
                    results.put(league, 0);
                }
            } catch (Exception e) {
                log.error("❌ Помилка міграції бомбардирів для {}: {}", league, e.getMessage());
                results.put(league, 0);
            }
        }

        int total = results.values().stream().mapToInt(Integer::intValue).sum();
        log.info("✅ Міграція бомбардирів завершена: {} гравців для {} ліг збережено в БД", total, leagues.size());

        return results;
    }

    // ==================== ІНШЕ ====================

    public void removeDuplicateMatches() {
        log.info("🧹 Початок очищення дублікатів матчів...");
        try {
            List<MatchEntity> allMatches = matchDbService.list();
            if (allMatches.isEmpty()) {
                log.info("ℹ️ Матчі відсутні, очищення не потрібне");
                return;
            }

            Map<String, List<MatchEntity>> groupedMatches = allMatches.stream()
                    .collect(java.util.stream.Collectors.groupingBy(m ->
                            m.getHomeTeam().getId() + "-" + m.getAwayTeam().getId() + "-" + m.getKickoffAt()
                    ));

            int deletedCount = 0;
            for (List<MatchEntity> group : groupedMatches.values()) {
                if (group.size() > 1) {
                    for (int i = 1; i < group.size(); i++) {
                        matchDbService.delete(group.get(i).getId());
                        deletedCount++;
                    }
                }
            }

            if (deletedCount > 0) {
                log.info("✅ Видалено {} дублікатів матчів", deletedCount);
            } else {
                log.info("✅ Дублікатів не знайдено");
            }

        } catch (Exception e) {
            log.error("❌ Помилка під час очищення дублікатів: {}", e.getMessage(), e);
        }
    }

    public void removeUPLTeams() {
        log.info("🗑️ Видалення команд УПЛ з бази даних...");
        try {
            teamDbService.deleteTeamsByLeague("UPL");
            log.info("✅ Команди УПЛ видалено з бази даних");
        } catch (Exception e) {
            log.error("❌ Помилка видалення команд УПЛ: {}", e.getMessage(), e);
        }
    }

    private Integer getInteger(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }
}
