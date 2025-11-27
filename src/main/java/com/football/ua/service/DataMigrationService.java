package com.football.ua.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.football.ua.model.Team;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataMigrationService {
    private static final Logger log = LoggerFactory.getLogger(DataMigrationService.class);

    @Autowired
    private TeamDbService teamDbService;

    @Autowired
    private FileCacheService fileCacheService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
}

