package com.football.ua.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.football.ua.model.Team;
import com.football.ua.model.dto.FootballDataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.football.ua.aspect.ExternalApiCall;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ExternalTeamApiService {
    private static final Logger log = LoggerFactory.getLogger(ExternalTeamApiService.class);

    @Autowired
    private WebClient footballApiWebClient;

    @Autowired
    private FileCacheService fileCacheService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Value("${football.api.enabled:false}")
    private boolean apiEnabled;

    private Map<String, List<Team>> cachedTeams = null;
    private static final long CACHE_DURATION = 5 * 60 * 1000;
    private static final String ALL_TEAMS_CACHE_KEY = "all_teams";
    private static final Map<String, String> LEAGUE_CODES = Map.of(
            "EPL", "PL",
            "UCL", "CL",
            "LaLiga", "PD",
            "Bundesliga", "BL1",
            "SerieA", "SA",
            "Ligue1", "FL1"
    );

    private Map<String, Map<String, Object>> cachedStandings = new HashMap<>();
    private Map<String, Long> standingsUpdateTime = new HashMap<>();

    private List<Map<String, Object>> cachedUpcomingMatches = null;
    private long upcomingMatchesUpdateTime = 0;
    private final Map<String, Long> leagueUpdateTimestamps = new HashMap<>();

    private static final Map<String, String> LEAGUE_COLORS = Map.of(
            "UPL", "🔵🟡",
            "EPL", "🔵⚪",
            "UCL", "⭐🔵",
            "LaLiga", "🔴🟡",
            "Bundesliga", "🔴⚫",
            "SerieA", "🔵⚪",
            "Ligue1", "🔵🔴"
    );

    @ExternalApiCall
    public synchronized Map<String, List<Team>> getTeamsFromApi() {
        if (!apiEnabled) {
            log.info("API вимкнено, повертаємо локальні дані");
            Map<String, List<Team>> fallbackTeams = getFallbackTeams();
            cacheAggregatedResult(fallbackTeams, true);
            return fallbackTeams;
        }

        if (cachedTeams == null) {
            cachedTeams = new LinkedHashMap<>();
        }

        Map<String, List<Team>> allLeagues = new LinkedHashMap<>();
        List<Team> uplTeams = getFallbackTeamsForLeague("UPL");
        updateInMemoryLeagueCache("UPL", uplTeams, true);
        allLeagues.put("UPL", uplTeams);

        List<String> apiLeagues = Arrays.asList("UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");

        for (int i = 0; i < apiLeagues.size(); i++) {
            String leagueCode = apiLeagues.get(i);
            try {
                List<Team> leagueTeams = loadOrRefreshLeague(leagueCode);
                allLeagues.put(leagueCode, leagueTeams);
            } catch (Exception exception) {
                log.error("{}: помилка оновлення з API - {}", leagueCode, exception.getMessage());

                List<Team> staleCache = loadLeagueFromCacheIgnoringExpiration(leagueCode);
                if (staleCache != null && !staleCache.isEmpty()) {
                    log.warn("{}: використовуємо застарілі дані з кешу під час помилки API", leagueCode);
                    updateInMemoryLeagueCache(leagueCode, staleCache, false);
                    allLeagues.put(leagueCode, staleCache);
                } else {
                    List<Team> fallback = getFallbackTeamsForLeague(leagueCode);
                    updateInMemoryLeagueCache(leagueCode, fallback, false);
                    allLeagues.put(leagueCode, fallback);
                }
            }
        }

        cacheAggregatedResult(allLeagues, false);

        int totalTeams = allLeagues.values().stream().mapToInt(List::size).sum();
        log.info("Оновлено ліги поокремо: {} ліг, {} команд", allLeagues.size(), totalTeams);
        return allLeagues;
    }

    private List<Team> fetchTeamsForLeague(String leagueCode) {
        String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
        if (apiLeagueCode == null) {
            return new ArrayList<>();
        }

        try {
            log.debug("→ Запит: GET /competitions/{}/teams", apiLeagueCode);
            
            FootballDataResponse response = footballApiWebClient
                    .get()
                    .uri("/competitions/{code}/teams", apiLeagueCode)
                    .retrieve()
                    .bodyToMono(FootballDataResponse.class)
                    .doOnError(error -> log.error("Деталі помилки API: {}", error.getMessage()))
                    .block();

            if (response != null && response.getTeams() != null && !response.getTeams().isEmpty()) {
                int limit = leagueCode.equals("UCL") ? 50 : 20;
                List<Team> teams = response.getTeams().stream()
                        .limit(limit)
                        .map(teamData -> convertToTeam(teamData, leagueCode))
                        .collect(Collectors.toList());
                return teams;
            }

            return new ArrayList<>();
            
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null) {
                if (errorMsg.contains("429")) {
                    log.error("⚠️ HTTP 429: Перевищено ліміт запитів (10/хвилину)");
                } else if (errorMsg.contains("401") || errorMsg.contains("403")) {
                    log.error("🔒 HTTP 401/403: Невірний або відсутній API ключ");
                } else if (errorMsg.contains("404")) {
                    log.error("❌ HTTP 404: Ліга {} не знайдена", apiLeagueCode);
                } else {
                    log.error("❌ Помилка: {}", errorMsg);
                }
            }
            throw e;
        }
    }
    private List<Team> loadOrRefreshLeague(String leagueCode) {
        if (isLeagueFreshInMemory(leagueCode)) {
            return cachedTeams.get(leagueCode);
        }

        String leagueCacheKey = buildLeagueCacheKey(leagueCode);
        List<Team> cachedBackup = cachedTeams != null ? cachedTeams.get(leagueCode) : null;
        boolean cacheValid = fileCacheService.isCacheValid("teams", leagueCacheKey);
        if (cacheValid) {
            cachedBackup = loadLeagueFromCache(leagueCode);
            if (cachedBackup != null && !cachedBackup.isEmpty()) {
                log.debug("{}: повертаємо дані з валідного кешу", leagueCode);
                updateInMemoryLeagueCache(leagueCode, cachedBackup, true);
                return cachedBackup;
            }
        } else {
            List<Team> fileCopy = loadLeagueFromCacheIgnoringExpiration(leagueCode);
            if (fileCopy != null && !fileCopy.isEmpty()) {
                cachedBackup = fileCopy;
                log.debug("{}: використовуємо застарілі дані з кешу (вік перевищено)", leagueCode);
            }
        }

        rateLimiterService.acquire();

        try {
            List<Team> apiTeams = fetchTeamsForLeague(leagueCode);
            if (!apiTeams.isEmpty()) {
                saveLeagueToCache(leagueCode, apiTeams);
                updateInMemoryLeagueCache(leagueCode, apiTeams, true);
                return apiTeams;
            }
        } catch (Exception e) {
            log.error("Помилка завантаження ліги {}: {}", leagueCode, e.getMessage());

            if (cachedBackup != null && !cachedBackup.isEmpty()) {
                log.warn("{}: використовуємо застарілі дані з кешу через помилку API", leagueCode);
                updateInMemoryLeagueCache(leagueCode, cachedBackup, false);
                return cachedBackup;
            }
            throw e;
        }

        if (cachedBackup != null && !cachedBackup.isEmpty()) {
            log.warn("{}: використовуємо застарілі дані з кешу", leagueCode);
            updateInMemoryLeagueCache(leagueCode, cachedBackup, false);
            return cachedBackup;
        }

        return useBundledLeagueFallback(leagueCode);
    }

    private boolean isLeagueFreshInMemory(String leagueCode) {
        if (cachedTeams == null || !cachedTeams.containsKey(leagueCode)) {
            return false;
        }
        Long lastUpdate = leagueUpdateTimestamps.get(leagueCode);
        return lastUpdate != null && (System.currentTimeMillis() - lastUpdate) < CACHE_DURATION;
    }

    private void updateInMemoryLeagueCache(String leagueCode, List<Team> teams, boolean markFresh) {
        if (cachedTeams == null) {
            cachedTeams = new LinkedHashMap<>();
        }
        cachedTeams.put(leagueCode, teams);
        if (markFresh) {
            leagueUpdateTimestamps.put(leagueCode, System.currentTimeMillis());
        } else {
            leagueUpdateTimestamps.remove(leagueCode);
        }
    }

    private void cacheAggregatedResult(Map<String, List<Team>> data, boolean markFresh) {
        cachedTeams = new LinkedHashMap<>(data);
        if (markFresh) {
            long now = System.currentTimeMillis();
            data.keySet().forEach(code -> leagueUpdateTimestamps.put(code, now));
        }
        fileCacheService.saveToCache("teams", ALL_TEAMS_CACHE_KEY, data);
    }

    private String buildLeagueCacheKey(String leagueCode) {
        return "league_" + leagueCode.toLowerCase(Locale.ROOT);
    }

    @SuppressWarnings("unchecked")
    private List<Team> loadLeagueFromCache(String leagueCode) {
        try {
            Object cached = fileCacheService.loadFromCache("teams", buildLeagueCacheKey(leagueCode), List.class);
            if (cached instanceof List) {
                return (List<Team>) cached;
            }
        } catch (Exception exception) {
            log.warn("Не вдалося завантажити лігу {} з кешу: {}", leagueCode, exception.getMessage());
        }
        return null;
    }

    private void saveLeagueToCache(String leagueCode, List<Team> teams) {
        fileCacheService.saveToCache("teams", buildLeagueCacheKey(leagueCode), teams);
    }

    private List<Team> loadLeagueFromCacheIgnoringExpiration(String leagueCode) {
        try {
            Object cached = fileCacheService.loadFromCacheIgnoringExpiration("teams", buildLeagueCacheKey(leagueCode), List.class);
            if (cached instanceof List) {
                @SuppressWarnings("unchecked")
                List<Team> list = (List<Team>) cached;
                return list;
            }
        } catch (Exception exception) {
            log.warn("Не вдалося завантажити лігу {} з кешу (ігноруючи термін дії): {}", leagueCode, exception.getMessage());
        }
        return null;
    }

    private List<Team> useBundledLeagueFallback(String leagueCode) {
        List<Team> fallback = getFallbackTeamsForLeague(leagueCode);
        updateInMemoryLeagueCache(leagueCode, fallback, false);
        return fallback;
    }

    private Team convertToTeam(FootballDataResponse.TeamData teamData, String leagueCode) {
        Team team = new Team();
        
        team.id = teamData.getId();
        team.name = teamData.getName();
        team.league = leagueCode;
        
        String address = teamData.getAddress();
        if (address != null && !address.isEmpty()) {
            String[] parts = address.split(",");
            team.city = parts[parts.length - 1].trim();
        } else {
            team.city = "";
        }
        
        team.colors = teamData.getClubColors() != null ? 
                      convertClubColorsToEmojis(teamData.getClubColors()) : 
                      LEAGUE_COLORS.getOrDefault(leagueCode, "⚪");
        
        team.emblemUrl = teamData.getCrest() != null ? teamData.getCrest() : "";

        return team;
    }

    private String convertClubColorsToEmojis(String clubColors) {
        if (clubColors == null) return "⚪";
        
        String colors = clubColors.toLowerCase();
        StringBuilder emojis = new StringBuilder();
        
        if (colors.contains("red")) emojis.append("🔴");
        if (colors.contains("blue")) emojis.append("🔵");
        if (colors.contains("yellow") || colors.contains("gold")) emojis.append("🟡");
        if (colors.contains("green")) emojis.append("🟢");
        if (colors.contains("white")) emojis.append("⚪");
        if (colors.contains("black")) emojis.append("⚫");
        if (colors.contains("orange")) emojis.append("🟠");
        if (colors.contains("purple") || colors.contains("violet")) emojis.append("🟣");
        
        return emojis.length() > 0 ? emojis.toString() : "⚪";
    }

    private Map<String, List<Team>> getFallbackTeams() {
        Map<String, List<Team>> leagues = new LinkedHashMap<>();
        leagues.put("UPL", getFallbackTeamsForLeague("UPL"));
        leagues.put("UCL", getFallbackTeamsForLeague("UCL"));
        leagues.put("EPL", getFallbackTeamsForLeague("EPL"));
        leagues.put("LaLiga", getFallbackTeamsForLeague("LaLiga"));
        leagues.put("Bundesliga", getFallbackTeamsForLeague("Bundesliga"));
        leagues.put("SerieA", getFallbackTeamsForLeague("SerieA"));
        leagues.put("Ligue1", getFallbackTeamsForLeague("Ligue1"));
        return leagues;
    }

    private List<Team> getFallbackTeamsForLeague(String leagueCode) {
        switch (leagueCode) {
            case "UPL":
                return Arrays.asList(
                        createTeam(1, "Динамо Київ", "UPL", "Київ", "🔵⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/FC_Dynamo_Kyiv_logo.svg/100px-FC_Dynamo_Kyiv_logo.svg.png"),
                        createTeam(2, "Шахтар Донецьк", "UPL", "Донецьк", "🟠⚫", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a1/FC_Shakhtar_Donetsk.svg/100px-FC_Shakhtar_Donetsk.svg.png"),
                        createTeam(3, "Дніпро-1", "UPL", "Дніпро", "🔵⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f8/FC_Dnipro-1_logo.svg/100px-FC_Dnipro-1_logo.svg.png"),
                        createTeam(4, "Ворскла", "UPL", "Полтава", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/8/82/FC_Vorskla_Poltava_logo.svg/100px-FC_Vorskla_Poltava_logo.svg.png"),
                        createTeam(5, "Зоря", "UPL", "Луганськ", "⚫⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/4/4c/FC_Zorya_Luhansk_logo.svg/100px-FC_Zorya_Luhansk_logo.svg.png"),
                        createTeam(6, "Олександрія", "UPL", "Олександрія", "🟡🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/7/79/FC_Olexandriya_logo.svg/100px-FC_Olexandriya_logo.svg.png"),
                        createTeam(7, "Колос", "UPL", "Ковалівка", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/5a/FC_Kolos_Kovalivka_logo.svg/100px-FC_Kolos_Kovalivka_logo.svg.png"),
                        createTeam(8, "Рух", "UPL", "Львів", "🟡🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/4/4f/FC_Rukh_Lviv_logo.svg/100px-FC_Rukh_Lviv_logo.svg.png"),
                        createTeam(9, "Кривбас", "UPL", "Кривий Ріг", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/7/79/FC_Kryvbas_Kryvyi_Rih_logo.svg/100px-FC_Kryvbas_Kryvyi_Rih_logo.svg.png"),
                        createTeam(10, "Минай", "UPL", "Мінай", "🔴🟡", "https://upload.wikimedia.org/wikipedia/en/thumb/c/c7/FC_Mynai_logo.svg/100px-FC_Mynai_logo.svg.png"),
                        createTeam(11, "Чорноморець", "UPL", "Одеса", "🔵⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/6/60/FC_Chornomorets_Odesa_logo.svg/100px-FC_Chornomorets_Odesa_logo.svg.png"),
                        createTeam(12, "Металіст 1925", "UPL", "Харків", "🟡🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/8/8f/FC_Metalist_1925_Kharkiv_logo.svg/100px-FC_Metalist_1925_Kharkiv_logo.svg.png"),
                        createTeam(13, "Верес", "UPL", "Рівне", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a7/FC_Veres_Rivne_logo.svg/100px-FC_Veres_Rivne_logo.svg.png"),
                        createTeam(14, "Інгулець", "UPL", "Петрове", "🟡⚫", "https://upload.wikimedia.org/wikipedia/en/thumb/6/6c/FC_Inhulets_Petrove_logo.svg/100px-FC_Inhulets_Petrove_logo.svg.png"),
                        createTeam(15, "ЛНЗ", "UPL", "Черкаси", "🔵🟡", "https://upload.wikimedia.org/wikipedia/en/thumb/f/fd/FC_LNZ_Cherkasy_logo.svg/100px-FC_LNZ_Cherkasy_logo.svg.png"),
                        createTeam(16, "Полісся", "UPL", "Житомир", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/0/0e/FC_Polissya_Zhytomyr_logo.svg/100px-FC_Polissya_Zhytomyr_logo.svg.png")
                );
            case "UCL":
                return Arrays.asList(
                        createTeam(17, "Реал Мадрид", "UCL", "Мадрид", "⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/56/Real_Madrid_CF.svg/100px-Real_Madrid_CF.svg.png"),
                        createTeam(18, "Манчестер Сіті", "UCL", "Манчестер", "🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/e/eb/Manchester_City_FC_badge.svg/100px-Manchester_City_FC_badge.svg.png"),
                        createTeam(19, "Баварія", "UCL", "Мюнхен", "🔴⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/FC_Bayern_M%C3%BCnchen_logo_%282017%29.svg/100px-FC_Bayern_M%C3%BCnchen_logo_%282017%29.svg.png"),
                        createTeam(20, "ПСЖ", "UCL", "Париж", "🔵🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a7/Paris_Saint-Germain_F.C..svg/100px-Paris_Saint-Germain_F.C..svg.png"),
                        createTeam(21, "Інтер", "UCL", "Мілан", "🔵⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/FC_Internazionale_Milano_2021.svg/100px-FC_Internazionale_Milano_2021.svg.png"),
                        createTeam(22, "Барселона", "UCL", "Барселона", "🔴🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/4/47/FC_Barcelona_%28crest%29.svg/100px-FC_Barcelona_%28crest%29.svg.png"),
                        createTeam(23, "Арсенал", "UCL", "Лондон", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/53/Arsenal_FC.svg/100px-Arsenal_FC.svg.png"),
                        createTeam(24, "Атлетіко", "UCL", "Мадрид", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/f/f4/Atletico_Madrid_2017_logo.svg/100px-Atletico_Madrid_2017_logo.svg.png"),
                        createTeam(25, "Ліверпуль", "UCL", "Ліверпуль", "🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/100px-Liverpool_FC.svg.png"),
                        createTeam(26, "Боруссія Д", "UCL", "Дортмунд", "🟡⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Borussia_Dortmund_logo.svg/100px-Borussia_Dortmund_logo.svg.png"),
                        createTeam(27, "Ювентус", "UCL", "Турин", "⚪⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b6/Juventus_FC_2017_logo.svg/100px-Juventus_FC_2017_logo.svg.png"),
                        createTeam(28, "Бенфіка", "UCL", "Лісабон", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a2/SL_Benfica_logo.svg/100px-SL_Benfica_logo.svg.png")
                );
            case "EPL":
                return Arrays.asList(
                        createTeam(29, "Манчестер Сіті", "EPL", "Манчестер", "🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/e/eb/Manchester_City_FC_badge.svg/100px-Manchester_City_FC_badge.svg.png"),
                        createTeam(30, "Арсенал", "EPL", "Лондон", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/53/Arsenal_FC.svg/100px-Arsenal_FC.svg.png"),
                        createTeam(31, "Ліверпуль", "EPL", "Ліверпуль", "🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/100px-Liverpool_FC.svg.png"),
                        createTeam(32, "Астон Вілла", "EPL", "Бірмінгем", "🟣", "https://upload.wikimedia.org/wikipedia/en/thumb/f/f9/Aston_Villa_FC_crest_%282016%29.svg/100px-Aston_Villa_FC_crest_%282016%29.svg.png"),
                        createTeam(33, "Тоттенгем", "EPL", "Лондон", "⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/b/b4/Tottenham_Hotspur.svg/100px-Tottenham_Hotspur.svg.png"),
                        createTeam(34, "Челсі", "EPL", "Лондон", "🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/c/cc/Chelsea_FC.svg/100px-Chelsea_FC.svg.png"),
                        createTeam(35, "Ньюкасл", "EPL", "Ньюкасл", "⚫⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/56/Newcastle_United_Logo.svg/100px-Newcastle_United_Logo.svg.png"),
                        createTeam(36, "Манчестер Юнайтед", "EPL", "Манчестер", "🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/7/7a/Manchester_United_FC_crest.svg/100px-Manchester_United_FC_crest.svg.png"),
                        createTeam(37, "Вест Хем", "EPL", "Лондон", "🟣🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/c/c2/West_Ham_United_FC_logo.svg/100px-West_Ham_United_FC_logo.svg.png"),
                        createTeam(38, "Брайтон", "EPL", "Брайтон", "🔵⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/f/fd/Brighton_%26_Hove_Albion_logo.svg/100px-Brighton_%26_Hove_Albion_logo.svg.png")
                );
            case "LaLiga":
                return Arrays.asList(
                        createTeam(41, "Реал Мадрид", "LaLiga", "Мадрид", "⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/56/Real_Madrid_CF.svg/100px-Real_Madrid_CF.svg.png"),
                        createTeam(42, "Барселона", "LaLiga", "Барселона", "🔴🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/4/47/FC_Barcelona_%28crest%29.svg/100px-FC_Barcelona_%28crest%29.svg.png"),
                        createTeam(43, "Атлетіко Мадрид", "LaLiga", "Мадрид", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/f/f4/Atletico_Madrid_2017_logo.svg/100px-Atletico_Madrid_2017_logo.svg.png"),
                        createTeam(44, "Севілья", "LaLiga", "Севілья", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/3/3b/Sevilla_FC_logo.svg/100px-Sevilla_FC_logo.svg.png"),
                        createTeam(45, "Валенсія", "LaLiga", "Валенсія", "⚪🟠", "https://upload.wikimedia.org/wikipedia/en/thumb/c/ce/Valenciacf.svg/100px-Valenciacf.svg.png")
                );
            case "Bundesliga":
                return Arrays.asList(
                        createTeam(46, "Баварія", "Bundesliga", "Мюнхен", "🔴⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/FC_Bayern_M%C3%BCnchen_logo_%282017%29.svg/100px-FC_Bayern_M%C3%BCnchen_logo_%282017%29.svg.png"),
                        createTeam(47, "Боруссія Дортмунд", "Bundesliga", "Дортмунд", "🟡⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Borussia_Dortmund_logo.svg/100px-Borussia_Dortmund_logo.svg.png"),
                        createTeam(48, "РБ Лейпциг", "Bundesliga", "Лейпциг", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/0/04/RB_Leipzig_2014_logo.svg/100px-RB_Leipzig_2014_logo.svg.png"),
                        createTeam(49, "Баєр Леверкузен", "Bundesliga", "Леверкузен", "🔴⚫", "https://upload.wikimedia.org/wikipedia/en/thumb/5/59/Bayer_04_Leverkusen_logo.svg/100px-Bayer_04_Leverkusen_logo.svg.png"),
                        createTeam(50, "Юніон Берлін", "Bundesliga", "Берлін", "🔴⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/44/1._FC_Union_Berlin_Logo.svg/100px-1._FC_Union_Berlin_Logo.svg.png")
                );
            case "SerieA":
                return Arrays.asList(
                        createTeam(51, "Ювентус", "SerieA", "Турин", "⚪⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b6/Juventus_FC_2017_logo.svg/100px-Juventus_FC_2017_logo.svg.png"),
                        createTeam(52, "Інтер", "SerieA", "Мілан", "🔵⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/FC_Internazionale_Milano_2021.svg/100px-FC_Internazionale_Milano_2021.svg.png"),
                        createTeam(53, "Мілан", "SerieA", "Мілан", "🔴⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d0/Logo_of_AC_Milan.svg/100px-Logo_of_AC_Milan.svg.png"),
                        createTeam(54, "Наполі", "SerieA", "Неаполь", "🔵", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/2d/SSC_Neapel.svg/100px-SSC_Neapel.svg.png"),
                        createTeam(55, "Рома", "SerieA", "Рим", "🟡🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/f/f7/AS_Roma_logo_%282017%29.svg/100px-AS_Roma_logo_%282017%29.svg.png")
                );
            case "Ligue1":
                return Arrays.asList(
                        createTeam(56, "ПСЖ", "Ligue1", "Париж", "🔵🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a7/Paris_Saint-Germain_F.C..svg/100px-Paris_Saint-Germain_F.C..svg.png"),
                        createTeam(57, "Марсель", "Ligue1", "Марсель", "⚪🔵", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d8/Olympique_Marseille_logo.svg/100px-Olympique_Marseille_logo.svg.png"),
                        createTeam(58, "Ліон", "Ligue1", "Ліон", "🔴🔵⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/e/e2/Olympique_Lyonnais_logo.svg/100px-Olympique_Lyonnais_logo.svg.png"),
                        createTeam(59, "Монако", "Ligue1", "Монако", "🔴⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/4/48/Logo_AS_Monaco.svg/100px-Logo_AS_Monaco.svg.png"),
                        createTeam(60, "Лілль", "Ligue1", "Лілль", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/6/6d/Lille_OSC_logo.svg/100px-Lille_OSC_logo.svg.png")
                );
            default:
                return new ArrayList<>();
        }
    }

    private Team createTeam(int id, String name, String league, String city, String colors, String emblemUrl) {
        Team team = new Team();
        team.id = (long) id;
        team.name = name;
        team.league = league;
        team.city = city;
        team.colors = colors;
        team.emblemUrl = emblemUrl;
        return team;
    }

    public Map<String, Object> getLeagueStandings(String leagueCode) {

        String cacheKey = leagueCode.toLowerCase();

        if (fileCacheService.isCacheValid("standings", cacheKey)) {
            try {
                Object cached = fileCacheService.loadFromCache("standings", cacheKey, Map.class);
                if (cached != null) {
                    Map<String, Object> cachedMap = (Map<String, Object>) cached;

                    Object standings = cachedMap.get("standings");
                    if (standings instanceof List && !((List<?>) standings).isEmpty()) {
                        log.debug("📦 Повертаємо закешовану турнірну таблицю для {} з файлу", leagueCode);
                        return cachedMap;
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Помилка завантаження турнірної таблиці з кешу: {}", e.getMessage());
            }
        }

        Map<String, Object> staleCache = null;
        try {
            Object cachedData = fileCacheService.loadFromCacheIgnoringExpiration("standings", cacheKey, Map.class);
            if (cachedData != null) {
                Map<String, Object> cachedMap = (Map<String, Object>) cachedData;
                Object standings = cachedMap.get("standings");
                if (standings instanceof List && !((List<?>) standings).isEmpty()) {
                    staleCache = cachedMap;
                    log.debug("📦 Знайдено застарілий кеш для {} (буде використано якщо API недоступний)", leagueCode);
                }
            }
        } catch (Exception e) {
            log.debug("⚠️ Не вдалося завантажити застарілий кеш: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();

        if ("UPL".equals(leagueCode)) {
            result.put("league", "UPL");
            result.put("standings", new ArrayList<>());
            result.put("source", "local");

            return result;
        }

        if (!apiEnabled) {
            result.put("league", leagueCode);
            result.put("standings", new ArrayList<>());
            result.put("source", "local");

            return result;
        }

        String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
        if (apiLeagueCode == null) {
            result.put("league", leagueCode);
            result.put("standings", new ArrayList<>());
            result.put("source", "local");

            return result;
        }

        try {
            log.info("→ Запит: GET /competitions/{}/standings", apiLeagueCode);
            
            rateLimiterService.acquire();

            Map<String, Object> response = footballApiWebClient
                    .get()
                    .uri("/competitions/{code}/standings", apiLeagueCode)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("standings")) {
                List<Map<String, Object>> standings = (List<Map<String, Object>>) response.get("standings");
                
                if (standings != null && !standings.isEmpty()) {
                    Map<String, Object> totalStandings = standings.get(0);
                    List<Map<String, Object>> table = (List<Map<String, Object>>) totalStandings.get("table");
                    
                    if (table != null) {
                        List<Map<String, Object>> formattedTable = table.stream()
                                .map(entry -> {
                                    Map<String, Object> formatted = new HashMap<>();
                                    Map<String, Object> team = (Map<String, Object>) entry.get("team");
                                    
                                    if (team != null) {
                                        formatted.put("position", entry.get("position"));
                                        formatted.put("teamName", team.get("name"));
                                        formatted.put("teamCrest", team.get("crest"));
                                        formatted.put("playedGames", entry.get("playedGames"));
                                        formatted.put("won", entry.get("won"));
                                        formatted.put("draw", entry.get("draw"));
                                        formatted.put("lost", entry.get("lost"));
                                        formatted.put("goalsFor", entry.get("goalsFor"));
                                        formatted.put("goalsAgainst", entry.get("goalsAgainst"));
                                        formatted.put("goalDifference", entry.get("goalDifference"));
                                        formatted.put("points", entry.get("points"));
                                    }
                                    
                                    return formatted;
                                })
                                .collect(Collectors.toList());
                        
                        result.put("league", leagueCode);
                        result.put("standings", formattedTable);
                        result.put("source", "api");

                        if (formattedTable != null && !formattedTable.isEmpty()) {
                            fileCacheService.saveToCache("standings", cacheKey, result);
                            log.info("✅ Отримано турнірну таблицю для {} ({} команд) - збережено в кеш", leagueCode, formattedTable.size());
                        } else {
                            log.warn("⚠️ Отримано порожню таблицю для {} - не зберігаємо в кеш", leagueCode);
                        }
                        return result;
                    }
                }
            }

            throw new RuntimeException("Порожня відповідь від API");

        } catch (Exception e) {
            log.error("❌ Помилка отримання турнірної таблиці для {}: {}", leagueCode, e.getMessage());

            if (staleCache != null) {
                log.info("📦 Повертаємо застарілі дані з кешу для {} (API недоступний)", leagueCode);
                return staleCache;
            }

            try {
                Object cachedData = fileCacheService.loadFromCacheIgnoringExpiration("standings", cacheKey, Map.class);
                if (cachedData != null) {
                    Map<String, Object> cachedMap = (Map<String, Object>) cachedData;
                    Object standings = cachedMap.get("standings");
                    if (standings instanceof List && !((List<?>) standings).isEmpty()) {
                        log.info("📦 Повертаємо застарілі дані з кешу для {} (API недоступний)", leagueCode);
                        return cachedMap;
                    } else {
                        log.warn("⚠️ Кеш для {} містить порожні дані (standings порожній), дозволяємо JavaScript згенерувати таблицю з локальних матчів", leagueCode);
                    }
                }
            } catch (Exception cacheError) {
                log.warn("⚠️ Помилка читання з кешу: {}", cacheError.getMessage());
            }


            result.put("league", leagueCode);
            result.put("standings", new ArrayList<>());
            result.put("source", "cache_empty");
            result.put("error", e.getMessage());



            return result;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUpcomingMatches() {
        return getMatchesByMatchday("upcoming", 0, "SCHEDULED");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPreviousMatches() {



        return getMatchesByMatchday("current_tour", 0, null);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUpcomingMatchesForLeague(String leagueCode) {
        return getMatchesByMatchdayForLeague(leagueCode, "upcoming", 0, "SCHEDULED");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPreviousMatchesForLeague(String leagueCode) {

        return getMatchesByMatchdayForLeague(leagueCode, "current_tour", 0, null);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMatchesByMatchdayForLeague(String leagueCode, String type, int matchdayOffset, String statusFilter) {
        String cacheKey = type + "_matches_" + leagueCode.toLowerCase();

        if (fileCacheService.isCacheValid("matches", cacheKey)) {
            try {
                Object cached = fileCacheService.loadFromCache("matches", cacheKey, List.class);
                if (cached != null) {
                    log.debug("📦 Повертаємо закешовані {} матчі для {} з файлу", type, leagueCode);
                    return (List<Map<String, Object>>) cached;
                }
            } catch (Exception e) {
                log.warn("⚠️ Помилка завантаження з кешу: {}", e.getMessage());
            }
        }

        if (!apiEnabled) {
            log.info("API вимкнено, повертаємо порожній список матчів для {}", leagueCode);
            List<Map<String, Object>> empty = new ArrayList<>();

            return empty;
        }

        if ("UPL".equals(leagueCode)) {
            log.debug("UPL: немає API, повертаємо порожній список");
            return new ArrayList<>();
        }

        String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
        if (apiLeagueCode == null) {
            log.warn("⚠️ {}: невідомий код ліги", leagueCode);
            return new ArrayList<>();
        }

        log.info("⚡ Завантажуємо {} матчі для {}...", type, leagueCode);

        try {

            Integer currentMatchday = getCurrentMatchday(apiLeagueCode);
            if (currentMatchday == null) {
                log.warn("⚠️ {}: не вдалося визначити поточний тур", leagueCode);

                try {
                    Object cachedData = fileCacheService.loadFromCacheIgnoringExpiration("matches", cacheKey, List.class);
                    if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                        log.info("📦 API недоступний, повертаємо застарілі дані з кешу для {}", leagueCode);
                        return (List<Map<String, Object>>) cachedData;
                    }
                } catch (Exception cacheError) {
                    log.warn("⚠️ Помилка читання застарілого кешу: {}", cacheError.getMessage());
                }
                return new ArrayList<>();
            }

            int targetMatchday = currentMatchday + matchdayOffset;
            if (targetMatchday < 1) {
                log.debug("⚠️ {}: тур {} менше 1, пропускаємо", leagueCode, targetMatchday);

                try {
                    Object cachedData = fileCacheService.loadFromCacheIgnoringExpiration("matches", cacheKey, List.class);
                    if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                        log.info("📦 Тур менше 1, повертаємо застарілі дані з кешу для {}", leagueCode);
                        return (List<Map<String, Object>>) cachedData;
                    }
                } catch (Exception cacheError) {
                    log.warn("⚠️ Помилка читання застарілого кешу: {}", cacheError.getMessage());
                }
                return new ArrayList<>();
            }

            log.debug("→ Запит: GET /competitions/{}/matches (тур {}, статус: {})",
                     apiLeagueCode, targetMatchday, statusFilter);

            rateLimiterService.acquire();

            FootballDataResponse response = footballApiWebClient
                    .get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                            .path("/competitions/{code}/matches")
                            .queryParam("matchday", targetMatchday);
                        
                        if (statusFilter != null) {
                            builder.queryParam("status", statusFilter);
                        }
                        
                        return builder.build(apiLeagueCode);
                    })
                    .retrieve()
                    .bodyToMono(FootballDataResponse.class)
                    .doOnError(error -> log.error("Помилка API для {}: {}",
                                                 leagueCode, error.getMessage()))
                    .block();

            List<Map<String, Object>> matches = new ArrayList<>();
            if (response != null && response.getMatches() != null) {
                matches = response.getMatches().stream()
                        .map(match -> convertMatchToMap(match, leagueCode))
                        .collect(Collectors.toList());

                    log.info("✅ {}: {} матчів (тур {})", leagueCode, matches.size(), targetMatchday);
                } else {
                    log.warn("⚠️ {}: порожня відповідь (тур {})", leagueCode, targetMatchday);
                }

                if (matches != null && !matches.isEmpty()) {
                    fileCacheService.saveToCache("matches", cacheKey, matches);
                    log.debug("💾 Збережено {} матчів для {} в кеш", matches.size(), leagueCode);
                } else {
                    log.debug("⚠️ Не зберігаємо порожні матчі для {} в кеш", leagueCode);


                    if ("upcoming".equals(type) && matchdayOffset < 2) {
                        log.info("🔄 {}: у турі {} немає запланованих матчів, перевіряємо наступний...", leagueCode, targetMatchday);
                        return getMatchesByMatchdayForLeague(leagueCode, type, matchdayOffset + 1, statusFilter);
                    }
                }

                return matches;

        } catch (Exception e) {
            log.error("❌ Помилка завантаження матчів для {}: {}", leagueCode, e.getMessage());

            try {

                Object cachedData = fileCacheService.loadFromCache("matches", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо дані з валідного кешу для {}", leagueCode);
                    return (List<Map<String, Object>>) cachedData;
                }

                cachedData = fileCacheService.loadFromCacheIgnoringExpiration("matches", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо застарілі дані з кешу для {} (API недоступний)", leagueCode);
                    return (List<Map<String, Object>>) cachedData;
                }
            } catch (Exception cacheError) {
                log.warn("⚠️ Помилка читання з кешу: {}", cacheError.getMessage());
            }

            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMatchesByMatchday(String type, int matchdayOffset, String statusFilter) {
        String cacheKey = type + "_matches_by_matchday";

        if (fileCacheService.isCacheValid("matches", cacheKey)) {
            try {
                Object cached = fileCacheService.loadFromCache("matches", cacheKey, List.class);
                if (cached != null) {
                    log.debug("📦 Повертаємо закешовані {} матчі з файлу", type);
                    return (List<Map<String, Object>>) cached;
                }
            } catch (Exception e) {
                log.warn("⚠️ Помилка завантаження з кешу: {}", e.getMessage());
            }
        }

        if (!apiEnabled) {
            log.info("API вимкнено, повертаємо порожній список матчів");
            List<Map<String, Object>> empty = new ArrayList<>();

            return empty;
        }

        log.info("⚡ Завантажуємо {} матчі по турах...", type);
        
        try {
            List<Map<String, Object>> allMatches = new ArrayList<>();
            List<String> apiLeagues = Arrays.asList("UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");
            
            for (String leagueCode : apiLeagues) {
                try {
                    List<Map<String, Object>> leagueMatches = getMatchesByMatchdayForLeague(leagueCode, type, matchdayOffset, statusFilter);
                    if (leagueMatches != null) {
                        allMatches.addAll(leagueMatches);
                    }
                } catch (Exception e) {
                    log.error("❌ {}: помилка отримання матчів - {}", leagueCode, e.getMessage());
                }
            }

            allMatches.sort((m1, m2) -> {
                Object k1 = m1.get("kickoffAt");
                Object k2 = m2.get("kickoffAt");
                if (k1 == null || k2 == null) return 0;
                if (k1 instanceof LocalDateTime && k2 instanceof LocalDateTime) {
                    return ((LocalDateTime) k1).compareTo((LocalDateTime) k2);
                }
                return k1.toString().compareTo(k2.toString());
            });

            if (allMatches != null && !allMatches.isEmpty()) {
                fileCacheService.saveToCache("matches", cacheKey, allMatches);
                log.info("🎯 Завантажено {} {} матчів. Збережено в файловий кеш на 30 хв", 
                        allMatches.size(), type);
            } else {
                log.warn("⚠️ Не зберігаємо порожні {} матчі в кеш", type);
            }
            return allMatches;
            
        } catch (Exception e) {
            log.error("❌ Критична помилка завантаження матчів: {}", e.getMessage());

            try {
                Object cachedData = fileCacheService.loadFromCache("matches", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо застарілі дані з кешу замість порожнього результату");
                    return (List<Map<String, Object>>) cachedData;
                }
            } catch (Exception cacheError) {
                log.warn("⚠️ Помилка читання з кешу: {}", cacheError.getMessage());
            }

            log.warn("⚠️ Немає даних у кеші, повертаємо порожній список");
            return new ArrayList<>();
        }
    }
    
    private Integer getCurrentMatchday(String apiLeagueCode) {
        try {
            Map<String, Object> response = footballApiWebClient
                    .get()
                    .uri("/competitions/{code}/standings", apiLeagueCode)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            
            if (response != null && response.containsKey("season")) {
                Map<String, Object> season = (Map<String, Object>) response.get("season");
                if (season != null && season.containsKey("currentMatchday")) {
                    return (Integer) season.get("currentMatchday");
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Помилка отримання поточного туру для {}: {}", apiLeagueCode, e.getMessage());
        }
        return null;
    }

    private Map<String, Object> convertMatchToMap(FootballDataResponse.MatchData matchData, String leagueCode) {
        Map<String, Object> match = new HashMap<>();
        
        match.put("id", matchData.getId());
        match.put("league", leagueCode);
        match.put("status", matchData.getStatus());
        match.put("matchday", matchData.getMatchday());
        
        if (matchData.getUtcDate() != null) {
            try {
                LocalDateTime kickoff = LocalDateTime.parse(
                    matchData.getUtcDate(), 
                    DateTimeFormatter.ISO_DATE_TIME
                );
                match.put("kickoffAt", kickoff);
                match.put("date", kickoff.toLocalDate().toString());
                match.put("time", kickoff.toLocalTime().toString());
            } catch (Exception e) {
                log.warn("Помилка парсингу дати: {}", matchData.getUtcDate());
                match.put("kickoffAt", null);
            }
        }
        
        if (matchData.getHomeTeam() != null) {
            Map<String, Object> homeTeam = new HashMap<>();
            homeTeam.put("id", matchData.getHomeTeam().getId());
            homeTeam.put("name", matchData.getHomeTeam().getName());
            homeTeam.put("shortName", matchData.getHomeTeam().getShortName());
            homeTeam.put("crest", matchData.getHomeTeam().getCrest());
            match.put("homeTeam", homeTeam);
        }
        
        if (matchData.getAwayTeam() != null) {
            Map<String, Object> awayTeam = new HashMap<>();
            awayTeam.put("id", matchData.getAwayTeam().getId());
            awayTeam.put("name", matchData.getAwayTeam().getName());
            awayTeam.put("shortName", matchData.getAwayTeam().getShortName());
            awayTeam.put("crest", matchData.getAwayTeam().getCrest());
            match.put("awayTeam", awayTeam);
        }
        
        if (matchData.getScore() != null && matchData.getScore().getFullTime() != null) {
            Map<String, Object> score = new HashMap<>();
            score.put("home", matchData.getScore().getFullTime().getHome());
            score.put("away", matchData.getScore().getFullTime().getAway());
            match.put("score", score);
        }
        
        return match;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTopScorersForLeague(String leagueCode) {
        String cacheKey = "scorers_" + leagueCode.toLowerCase();

        if (fileCacheService.isCacheValid("players", cacheKey)) {
            try {
                Object cached = fileCacheService.loadFromCache("players", cacheKey, List.class);
                if (cached != null) {
                    log.debug("📦 Повертаємо закешованих бомбардирів для {} з файлу", leagueCode);
                    return (List<Map<String, Object>>) cached;
                }
            } catch (Exception e) {
                log.warn("⚠️ Помилка завантаження з кешу: {}", e.getMessage());
            }
        }

        if (!apiEnabled) {
            log.info("API вимкнено, повертаємо порожній список бомбардирів для {}", leagueCode);
            List<Map<String, Object>> empty = new ArrayList<>();

            return empty;
        }

        if ("UPL".equals(leagueCode)) {
            log.debug("UPL: немає API, повертаємо порожній список бомбардирів");
            return new ArrayList<>();
        }

        String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
        if (apiLeagueCode == null) {
            log.warn("⚠️ {}: невідомий код ліги", leagueCode);
            return new ArrayList<>();
        }

        log.info("⚡ Завантажуємо топ бомбардирів для {}...", leagueCode);

        try {
            log.debug("→ Запит: GET /competitions/{}/scorers?limit=10", apiLeagueCode);

            rateLimiterService.acquire();

            Map<String, Object> response = footballApiWebClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/competitions/{code}/scorers")
                            .queryParam("limit", 10)
                            .build(apiLeagueCode))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnError(error -> log.error("Помилка API для {}: {}",
                                                 leagueCode, error.getMessage()))
                    .block();

            List<Map<String, Object>> scorers = new ArrayList<>();
            if (response != null) {
                Object scorersObj = response.get("scorers");

                if (scorersObj == null) {
                    log.warn("⚠️ Відповідь API не містить поля 'scorers', перевіряємо структуру: {}", response.keySet());
                }
                
                List<Map<String, Object>> scorersList = null;
                if (scorersObj instanceof List) {
                    scorersList = (List<Map<String, Object>>) scorersObj;
                } else if (response.containsKey("scorers")) {
                    scorersList = (List<Map<String, Object>>) response.get("scorers");
                }
                
                if (scorersList != null && !scorersList.isEmpty()) {
                    log.debug("📊 Отримано {} бомбардирів з API для {}", scorersList.size(), leagueCode);
                    
                    scorers = scorersList.stream()
                            .limit(10)
                            .map(scorerData -> {
                                Map<String, Object> scorer = new HashMap<>();

                                Map<String, Object> player = (Map<String, Object>) scorerData.get("player");
                                if (player != null) {
                                    scorer.put("name", player.get("name"));
                                    scorer.put("id", player.get("id"));
                                    scorer.put("nationality", player.get("nationality"));
                                    scorer.put("position", player.get("position"));
                                } else {
                                    log.warn("⚠️ Бомбардир без даних гравця: {}", scorerData);
                                }

                                Map<String, Object> team = (Map<String, Object>) scorerData.get("team");
                                if (team != null) {
                                    scorer.put("teamName", team.get("name"));
                                    scorer.put("teamId", team.get("id"));
                                    scorer.put("teamCrest", team.get("crest"));
                                } else {
                                    log.warn("⚠️ Бомбардир без даних команди: {}", scorerData);
                                }

                                scorer.put("goals", scorerData.get("goals") != null ? scorerData.get("goals") : 0);
                                scorer.put("assists", scorerData.get("assists") != null ? scorerData.get("assists") : 0);
                                scorer.put("penalties", scorerData.get("penalties") != null ? scorerData.get("penalties") : 0);
                                
                                return scorer;
                            })
                            .filter(scorer -> scorer.get("name") != null)
                            .collect(Collectors.toList());

                    log.info("✅ {}: {} бомбардирів успішно оброблено", leagueCode, scorers.size());
                } else {
                    log.warn("⚠️ Список бомбардирів порожній або null для {}", leagueCode);
                }
            } else {
                log.warn("⚠️ Відповідь API null для {}", leagueCode);
            }

            if (scorers != null && !scorers.isEmpty()) {
                fileCacheService.saveToCache("players", cacheKey, scorers);
                log.debug("💾 Збережено {} бомбардирів для {} в кеш", scorers.size(), leagueCode);
            } else {
                log.debug("⚠️ Не зберігаємо порожніх бомбардирів для {} в кеш", leagueCode);
            }

            return scorers;

        } catch (Exception e) {
            log.error("❌ Помилка завантаження бомбардирів для {}: {}", leagueCode, e.getMessage());

            try {

                Object cachedData = fileCacheService.loadFromCache("players", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо дані з валідного кешу для {}", leagueCode);
                    return (List<Map<String, Object>>) cachedData;
                }

                cachedData = fileCacheService.loadFromCacheIgnoringExpiration("players", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо застарілі дані з кешу для {} (API недоступний)", leagueCode);
                    return (List<Map<String, Object>>) cachedData;
                }
            } catch (Exception cacheError) {
                log.warn("⚠️ Помилка читання з кешу: {}", cacheError.getMessage());
            }

            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllMatchesForLeague(String leagueCode) {
        String cacheKey = "all_matches_" + leagueCode.toLowerCase();

        if (fileCacheService.isCacheValid("matches", cacheKey)) {
            try {
                Object cached = fileCacheService.loadFromCache("matches", cacheKey, List.class);
                if (cached != null) {
                    log.debug("📦 Повертаємо закешовані всі матчі для {} з файлу", leagueCode);
                    return (List<Map<String, Object>>) cached;
                }
            } catch (Exception e) {
                log.warn("⚠️ Помилка завантаження з кешу: {}", e.getMessage());
            }
        }

        if (!apiEnabled) {
            log.info("API вимкнено, повертаємо порожній список всіх матчів для {}", leagueCode);
            List<Map<String, Object>> empty = new ArrayList<>();

            return empty;
        }

        if ("UPL".equals(leagueCode)) {
            log.debug("UPL: немає API, повертаємо порожній список всіх матчів");
            return new ArrayList<>();
        }

        String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
        if (apiLeagueCode == null) {
            log.warn("⚠️ {}: невідомий код ліги", leagueCode);
            return new ArrayList<>();
        }

        log.info("⚡ Завантажуємо всі матчі сезону для {}...", leagueCode);

        try {
            log.debug("→ Запит: GET /competitions/{}/matches (завершені матчі, ліміт: 200)",
                     apiLeagueCode);

            List<Map<String, Object>> matches = new ArrayList<>();

            try {

                FootballDataResponse finishedResponse = footballApiWebClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/competitions/{code}/matches")
                                .queryParam("status", "FINISHED")
                                .queryParam("limit", 200)
                                .build(apiLeagueCode))
                        .retrieve()
                        .bodyToMono(FootballDataResponse.class)
                        .doOnError(error -> log.error("Помилка отримання завершених матчів для {}: {}",
                                                     leagueCode, error.getMessage()))
                        .block();

                if (finishedResponse != null && finishedResponse.getMatches() != null) {
                    List<Map<String, Object>> finishedMatches = finishedResponse.getMatches().stream()
                            .map(match -> convertMatchToMap(match, leagueCode))
                            .collect(Collectors.toList());
                    matches.addAll(finishedMatches);
                    log.info("✅ {}: отримано {} завершених матчів сезону", leagueCode, finishedMatches.size());
                }

                FootballDataResponse upcomingResponse = footballApiWebClient
                        .get()
                        .uri(uriBuilder -> uriBuilder
                                .path("/competitions/{code}/matches")
                                .queryParam("status", "SCHEDULED")
                                .queryParam("limit", 100)
                                .build(apiLeagueCode))
                        .retrieve()
                        .bodyToMono(FootballDataResponse.class)
                        .doOnError(error -> log.error("Помилка отримання майбутніх матчів для {}: {}",
                                                     leagueCode, error.getMessage()))
                        .block();

                if (upcomingResponse != null && upcomingResponse.getMatches() != null) {
                    List<Map<String, Object>> upcomingMatches = upcomingResponse.getMatches().stream()
                            .map(match -> convertMatchToMap(match, leagueCode))
                            .collect(Collectors.toList());
                    matches.addAll(upcomingMatches);
                    log.info("✅ {}: отримано {} майбутніх матчів сезону", leagueCode, upcomingMatches.size());
                }

                log.info("✅ {}: загалом отримано {} матчів сезону з API", leagueCode, matches.size());

            } catch (Exception e) {
                log.warn("Не вдалося отримати матчі сезону з API для {}: {}", leagueCode, e.getMessage());

                try {
                    List<Map<String, Object>> finishedMatches = getPreviousMatchesForLeague(leagueCode);
                    List<Map<String, Object>> upcomingMatches = getUpcomingMatchesForLeague(leagueCode);
                    matches.addAll(finishedMatches);
                    matches.addAll(upcomingMatches);
                    log.info("📦 Використано резервні методи: {} завершених + {} майбутніх матчів для {}",
                            finishedMatches.size(), upcomingMatches.size(), leagueCode);
                } catch (Exception fallbackError) {
                    log.error("Не вдалося отримати резервні дані для {}: {}", leagueCode, fallbackError.getMessage());
                }
            }

            if (matches != null && !matches.isEmpty()) {
                fileCacheService.saveToCache("matches", cacheKey, matches);
                log.debug("💾 Збережено {} матчів сезону для {} в кеш", matches.size(), leagueCode);
            } else {
                log.debug("⚠️ Не зберігаємо порожні матчі сезону для {} в кеш", leagueCode);
            }

            return matches;

        } catch (Exception e) {
            log.error("❌ Помилка завантаження всіх матчів сезону для {}: {}", leagueCode, e.getMessage());

            try {
                Object cachedData = fileCacheService.loadFromCache("matches", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо дані з валідного кешу для {}", leagueCode);
                    return (List<Map<String, Object>>) cachedData;
                }

                cachedData = fileCacheService.loadFromCacheIgnoringExpiration("matches", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо застарілі дані з кешу для {} (API недоступний)", leagueCode);
                    return (List<Map<String, Object>>) cachedData;
                }
            } catch (Exception cacheError) {
                log.warn("⚠️ Помилка читання з кешу: {}", cacheError.getMessage());
            }

            return new ArrayList<>();
        }
    }
}




