package com.football.ua.controller;

import com.football.ua.model.Team;
import com.football.ua.service.ExternalTeamApiService;
import com.football.ua.service.FileCacheService;
import com.football.ua.service.TeamDbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
@Tag(name = "Teams", description = "API для управління футбольними командами")
public class TeamController {
    private static final Logger log = LoggerFactory.getLogger(TeamController.class);
    private static final Map<Long, Team> db = new LinkedHashMap<>();
    private static long idSeq = 1;

    @Autowired
    private ExternalTeamApiService externalTeamApiService;

    @Autowired
    private FileCacheService fileCacheService;

    @Autowired
    private TeamDbService teamDbService;

    @GetMapping
    @Operation(summary = "Отримати список всіх команд", description = "Повертає список всіх збережених команд")
    public List<Team> list() {
        log.info("Отримано запит на список всіх команд");

        // Спочатку перевіряємо дані в БД (кешуються)
        Map<String, List<Team>> dbTeams = teamDbService.getAllTeams();
        if (!dbTeams.isEmpty()) {
            List<Team> allTeams = dbTeams.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());
            log.info("Повертаємо {} команд з БД (кешовано)", allTeams.size());
            return allTeams;
        }

        // Фолбек на статичний Map якщо БД порожня
        log.info("БД порожня, повертаємо {} команд зі статичного списку", db.size());
        return new ArrayList<>(db.values());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити нову команду", description = "Створює нову футбольну команду")
    public Team create(@RequestBody Team body) {
        log.info("Створюється нова команда: {}", body.name);
        body.id = idSeq++;
        db.put(body.id, body);
        log.info("Команду успішно створено з ID: {}", body.id);
        return body;
    }
    
    @GetMapping("/actual")
    @Operation(summary = "Отримати актуальні команди з ліг", description = "Повертає команди з БД або API")
    public Map<String, List<Team>> getActualTeams() {
        log.info("Отримано запит на актуальні команди");

        Map<String, List<Team>> dbTeams = teamDbService.getAllTeams();
        if (!dbTeams.isEmpty()) {
            log.info("Повертаємо дані з БД ({} ліг з {} командами)",
                    dbTeams.size(), dbTeams.values().stream().mapToInt(List::size).sum());
            return dbTeams;
        }

        log.info("Дані відсутні в БД, отримуємо з API");
        Map<String, List<Team>> leagues = externalTeamApiService.getTeamsFromApi();

        log.info("Повернуто {} ліг з {} командами з API", leagues.size(),
                 leagues.values().stream().mapToInt(List::size).sum());
        return leagues;
    }
    
    @GetMapping("/leagues")
    @Operation(summary = "Отримати список доступних ліг", description = "Повертає список кодів доступних футбольних ліг")
    public List<String> getLeagues() {
        log.info("Отримано запит на список ліг");
        List<String> leagues = Arrays.asList("UPL", "UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");
        log.debug("Повертається {} ліг", leagues.size());
        return leagues;
    }

    @GetMapping("/standings/{league}")
    @Operation(summary = "Отримати турнірну таблицю ліги", description = "Повертає турнірну таблицю ліги з API або локальні дані")
    public Map<String, Object> getLeagueStandings(@PathVariable String league) {
        log.info("Отримано запит на турнірну таблицю для ліги: {}", league);
        Map<String, Object> standings = externalTeamApiService.getLeagueStandings(league);
        log.info("Повернуто турнірну таблицю для ліги {}", league);
        return standings;
    }

    @GetMapping("/matches/upcoming")
    @Operation(summary = "Отримати майбутні матчі", description = "Повертає список майбутніх матчів поточного туру з усіх ліг")
    public Map<String, Object> getUpcomingMatches() {
        log.info("Отримано запит на майбутні матчі");
        List<Map<String, Object>> matches = externalTeamApiService.getUpcomingMatches();
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", matches.size());
        response.put("matches", matches);
        response.put("type", "upcoming");
        
        log.info("Повернуто {} майбутніх матчів", matches.size());
        return response;
    }

    @GetMapping("/matches/previous")
    @Operation(summary = "Отримати минулі матчі", description = "Повертає список минулих матчів попереднього туру з усіх ліг")
    public Map<String, Object> getPreviousMatches() {
        log.info("Отримано запит на минулі матчі");
        List<Map<String, Object>> matches = externalTeamApiService.getPreviousMatches();
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", matches.size());
        response.put("matches", matches);
        response.put("type", "previous");
        
        log.info("Повернуто {} минулих матчів", matches.size());
        return response;
    }

    @GetMapping("/matches/upcoming/{league}")
    @Operation(summary = "Отримати майбутні матчі для ліги", description = "Повертає список майбутніх матчів для конкретної ліги")
    public Map<String, Object> getUpcomingMatchesForLeague(@PathVariable String league) {
        log.info("Отримано запит на майбутні матчі для ліги: {}", league);
        List<Map<String, Object>> matches = externalTeamApiService.getUpcomingMatchesForLeague(league);
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", matches.size());
        response.put("matches", matches);
        response.put("type", "upcoming");
        response.put("league", league);
        
        log.info("Повернуто {} майбутніх матчів для ліги {}", matches.size(), league);
        return response;
    }

    @GetMapping("/matches/previous/{league}")
    @Operation(summary = "Отримати минулі матчі для ліги", description = "Повертає список минулих матчів для конкретної ліги")
    public Map<String, Object> getPreviousMatchesForLeague(@PathVariable String league) {
        log.info("Отримано запит на минулі матчі для ліги: {}", league);
        List<Map<String, Object>> matches = externalTeamApiService.getPreviousMatchesForLeague(league);
        
        Map<String, Object> response = new HashMap<>();
        response.put("total", matches.size());
        response.put("matches", matches);
        response.put("type", "previous");
        response.put("league", league);
        
        log.info("Повернуто {} минулих матчів для ліги {}", matches.size(), league);
        return response;
    }

    @GetMapping("/scorers/{league}")
    @Operation(summary = "Отримати топ бомбардирів ліги", description = "Повертає список топ бомбардирів для конкретної ліги")
    public Map<String, Object> getTopScorersForLeague(@PathVariable String league) {
        log.info("Отримано запит на топ бомбардирів для ліги: {}", league);
        List<Map<String, Object>> scorers = externalTeamApiService.getTopScorersForLeague(league);

        Map<String, Object> response = new HashMap<>();
        response.put("total", scorers.size());
        response.put("scorers", scorers);
        response.put("league", league);

        log.info("Повернуто {} бомбардирів для ліги {}", scorers.size(), league);
        return response;
    }

    @GetMapping("/matches/all/{league}")
    @Operation(summary = "Отримати всі матчі сезону для ліги", description = "Повертає список всіх матчів сезону для конкретної ліги")
    public Map<String, Object> getAllMatchesForLeague(@PathVariable String league) {
        log.info("Отримано запит на всі матчі сезону для ліги: {}", league);
        List<Map<String, Object>> matches = externalTeamApiService.getAllMatchesForLeague(league);

        Map<String, Object> response = new HashMap<>();
        response.put("total", matches.size());
        response.put("matches", matches);
        response.put("league", league);
        response.put("type", "all_season");

        log.info("Повернуто {} матчів сезону для ліги {}", matches.size(), league);
        return response;
    }

    @GetMapping("/cache/info")
    @Operation(summary = "Отримати інформацію про кеш", description = "Повертає статистику файлового кешу")
    public Map<String, Object> getCacheInfo() {
        log.info("Отримано запит на інформацію про кеш");

        Map<String, Object> cacheInfo = fileCacheService.getCacheInfo();

        Map<String, Boolean> cacheValidity = new HashMap<>();
        cacheValidity.put("teams", fileCacheService.isCacheValid("teams", "all_teams"));
        cacheValidity.put("standings_upl", fileCacheService.isCacheValid("standings", "UPL"));
        cacheValidity.put("matches_upcoming", fileCacheService.isCacheValid("matches", "upcoming_matches_by_matchday"));
        cacheValidity.put("matches_previous", fileCacheService.isCacheValid("matches", "previous_matches_by_matchday"));

        cacheInfo.put("cacheValidity", cacheValidity);

        log.info("Повернуто інформацію про кеш");
        return cacheInfo;
    }

    @DeleteMapping("/cache/clear")
    @Operation(summary = "Очистити весь кеш", description = "Видаляє всі файли з кешу")
    public Map<String, String> clearCache() {
        log.info("Отримано запит на очищення кешу");
        fileCacheService.clearAllCache();
        Map<String, String> response = new HashMap<>();
        response.put("message", "Кеш успішно очищено");
        log.info("Кеш успішно очищено");
        return response;
    }

    @DeleteMapping("/cache/clear/{category}/{key}")
    @Operation(summary = "Очистити конкретний кеш", description = "Видаляє конкретний файл з кешу за категорією та ключем")
    public Map<String, String> clearCache(@PathVariable String category, @PathVariable String key) {
        log.info("Отримано запит на очищення кешу: {}/{}", category, key);
        fileCacheService.clearCache(category, key);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Кеш " + category + "/" + key + " успішно очищено");
        log.info("Кеш {}/{} успішно очищено", category, key);
        return response;
    }
}


