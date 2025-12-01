package com.football.ua.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.football.ua.model.Team;
import com.football.ua.model.dto.FootballDataResponse;
import com.football.ua.model.entity.MatchEntity;
import com.football.ua.repo.TeamRepository;
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
    private DatabaseCacheService fileCacheService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private MatchDbService matchDbService;

    @Autowired
    private ScorerDbService scorerDbService;

    @Autowired
    private StandingDbService standingDbService;

    @Autowired
    private TeamRepository teamRepository;

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

    private static final Map<String, String> LEAGUE_COLORS = Map.of(
            "UPL", "🔵🟡",
            "EPL", "🔵⚪",
            "UCL", "⭐🔵",
            "LaLiga", "🔴🟡",
            "Bundesliga", "🔴⚫",
            "SerieA", "🔵⚪",
            "Ligue1", "🔵🔴"
    );

    private final Map<String, Long> leagueUpdateTimestamps = new HashMap<>();

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
        
        // 1. Спочатку пробуємо завантажити з БД
        try {
            List<com.football.ua.model.entity.StandingEntity> standingsFromDb = standingDbService.listByLeague(leagueCode);
            
            if (standingsFromDb != null && !standingsFromDb.isEmpty()) {
                log.info("✅ Повертаємо {} позицій турнірної таблиці з БД для ліги {}", standingsFromDb.size(), leagueCode);
                
                // Конвертуємо Entity в Map для відповіді
                List<Map<String, Object>> result = new ArrayList<>();
                for (com.football.ua.model.entity.StandingEntity standing : standingsFromDb) {
                    Map<String, Object> standingMap = new HashMap<>();
                    standingMap.put("position", standing.getPosition());
                    standingMap.put("teamName", standing.getTeamName());
                    standingMap.put("teamCrest", standing.getTeamCrest());
                    standingMap.put("playedGames", standing.getPlayedGames());
                    standingMap.put("won", standing.getWon());
                    standingMap.put("draw", standing.getDraw());
                    standingMap.put("lost", standing.getLost());
                    standingMap.put("goalsFor", standing.getGoalsFor());
                    standingMap.put("goalsAgainst", standing.getGoalsAgainst());
                    standingMap.put("goalDifference", standing.getGoalDifference());
                    standingMap.put("points", standing.getPoints());
                    result.add(standingMap);
                }
                
                Map<String, Object> response = new HashMap<>();
                response.put("league", leagueCode);
                response.put("standings", result);
                response.put("source", "database");
                return response;
            }
        } catch (Exception e) {
            log.warn("⚠️ Помилка завантаження турнірної таблиці з БД: {}", e.getMessage());
        }

        // 2. Якщо не знайдено в БД, перевіряємо файловий кеш
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

        // 3. Перевіряємо застарілий кеш як fallback
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

        // 4. Якщо нічого не знайдено, завантажуємо з API
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

    public List<Map<String, Object>> getUpcomingMatches() {
        log.info("Отримано запит на майбутні матчі");
        List<MatchEntity> allMatches = matchDbService.list();
        LocalDateTime now = LocalDateTime.now();

        Map<String, List<MatchEntity>> matchesByLeague = allMatches.stream()
                .filter(match -> match.getKickoffAt().isAfter(now))
                .collect(Collectors.groupingBy(MatchEntity::getLeague));

        return matchesByLeague.values().stream()
                .flatMap(matches -> filterMatchesByMatchday(matches, true).stream())
                .sorted(Comparator.comparing(MatchEntity::getKickoffAt))
                .map(this::convertMatchToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getPreviousMatches() {
        log.info("Отримано запит на минулі матчі");
        List<MatchEntity> allMatches = matchDbService.list();
        LocalDateTime now = LocalDateTime.now();

        Map<String, List<MatchEntity>> matchesByLeague = allMatches.stream()
                .filter(match -> match.getKickoffAt().isBefore(now))
                .collect(Collectors.groupingBy(MatchEntity::getLeague));

        return matchesByLeague.values().stream()
                .flatMap(matches -> filterMatchesByMatchday(matches, false).stream())
                .sorted(Comparator.comparing(MatchEntity::getKickoffAt).reversed())
                .map(this::convertMatchToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getUpcomingMatchesForLeague(String leagueCode) {
        log.info("Отримано запит на майбутні матчі для ліги: {}", leagueCode);
        List<MatchEntity> allMatches = matchDbService.list();
        LocalDateTime now = LocalDateTime.now();

        List<MatchEntity> leagueMatches = allMatches.stream()
                .filter(match -> match.getLeague().equals(leagueCode))
                .filter(match -> match.getKickoffAt().isAfter(now))
                .collect(Collectors.toList());

        return filterMatchesByMatchday(leagueMatches, true).stream()
                .sorted(Comparator.comparing(MatchEntity::getKickoffAt))
                .map(this::convertMatchToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getPreviousMatchesForLeague(String leagueCode) {
        log.info("Отримано запит на минулі матчі для ліги: {}", leagueCode);
        List<MatchEntity> allMatches = matchDbService.list();
        LocalDateTime now = LocalDateTime.now();

        List<MatchEntity> leagueMatches = allMatches.stream()
                .filter(match -> match.getLeague().equals(leagueCode))
                .filter(match -> match.getKickoffAt().isBefore(now))
                .collect(Collectors.toList());

        return filterMatchesByMatchday(leagueMatches, false).stream()
                .sorted(Comparator.comparing(MatchEntity::getKickoffAt).reversed())
                .map(this::convertMatchToMap)
                .collect(Collectors.toList());
    }

    public List<Map<String, Object>> getTopScorersForLeague(String leagueCode) {
        log.info("Отримано запит на топ бомбардирів для ліги: {}", leagueCode);
        
        try {
            // Спочатку пробуємо завантажити з БД
            List<com.football.ua.model.entity.ScorerEntity> scorersFromDb = scorerDbService.listByLeague(leagueCode);
            
            if (scorersFromDb != null && !scorersFromDb.isEmpty()) {
                log.info("✅ Повертаємо {} бомбардирів з БД для ліги {}", scorersFromDb.size(), leagueCode);
                
                // Конвертуємо Entity в Map для відповіді
                List<Map<String, Object>> result = new ArrayList<>();
                for (com.football.ua.model.entity.ScorerEntity scorer : scorersFromDb) {
                    Map<String, Object> scorerMap = new HashMap<>();
                    scorerMap.put("playerId", scorer.getPlayerId());
                    scorerMap.put("name", scorer.getPlayerName()); // Змінено з playerName на name для відповідності frontend
                    scorerMap.put("teamId", scorer.getTeamId());
                    scorerMap.put("teamName", scorer.getTeamName());

                    // Додаємо емблему команди, якщо можливо знайти команду за ID
                    String teamCrest = "";
                    if (scorer.getTeamId() != null) {
                        try {
                            Optional<com.football.ua.model.entity.TeamEntity> teamEntity = teamRepository.findById(scorer.getTeamId().longValue());
                            if (teamEntity.isPresent()) {
                                teamCrest = teamEntity.get().getEmblemUrl() != null ? teamEntity.get().getEmblemUrl() : "";
                            }
                        } catch (Exception e) {
                            log.debug("Не вдалося знайти емблему для команди з ID {}: {}", scorer.getTeamId(), e.getMessage());
                        }
                    }
                    scorerMap.put("teamCrest", teamCrest);

                    scorerMap.put("goals", scorer.getGoals());
                    scorerMap.put("assists", scorer.getAssists());
                    scorerMap.put("penalties", scorer.getPenalties());
                    scorerMap.put("league", scorer.getLeague());
                    result.add(scorerMap);
                }
                
                return result;
            }
            
            log.info("⚠️ Бомбардири не знайдені в БД для ліги {}, повертаємо порожній список", leagueCode);
            return new ArrayList<>();
            
        } catch (Exception e) {
            log.error("❌ Помилка отримання бомбардирів для {}: {}", leagueCode, e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Map<String, Object>> getAllMatchesForLeague(String leagueCode) {
        log.info("Отримано запит на всі матчі сезону для ліги: {}", leagueCode);
        List<MatchEntity> allMatches = matchDbService.list();
        
        return allMatches.stream()
                .filter(match -> match.getLeague().equals(leagueCode))
                .map(this::convertMatchToMap)
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Map<String, Object>> getAllMatchesSeason() {
        log.info("Отримано запит на всі матчі сезону (всі ліги)");
        List<MatchEntity> allMatches = matchDbService.list();
        
        return allMatches.stream()
                .map(this::convertMatchToMap)
                .collect(java.util.stream.Collectors.toList());
    }

    private List<MatchEntity> filterMatchesByMatchday(List<MatchEntity> matches, boolean upcoming) {
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }

        Optional<Integer> targetMatchdayOpt = upcoming
                ? matches.stream()
                        .map(MatchEntity::getMatchday)
                        .filter(Objects::nonNull)
                        .min(Integer::compareTo)
                : matches.stream()
                        .map(MatchEntity::getMatchday)
                        .filter(Objects::nonNull)
                        .max(Integer::compareTo);

        if (targetMatchdayOpt.isPresent()) {
            Integer targetMatchday = targetMatchdayOpt.get();
            List<MatchEntity> filteredByMatchday = matches.stream()
                    .filter(match -> targetMatchday.equals(match.getMatchday()))
                    .collect(Collectors.toList());
            if (!filteredByMatchday.isEmpty()) {
                return filteredByMatchday;
            }
        }

        LocalDate targetDate = upcoming
                ? matches.stream()
                        .map(match -> match.getKickoffAt().toLocalDate())
                        .min(LocalDate::compareTo)
                        .orElse(null)
                : matches.stream()
                        .map(match -> match.getKickoffAt().toLocalDate())
                        .max(LocalDate::compareTo)
                        .orElse(null);

        if (targetDate == null) {
            return matches;
        }

        return matches.stream()
                .filter(match -> match.getKickoffAt().toLocalDate().isEqual(targetDate))
                .collect(Collectors.toList());
    }

    private Map<String, Object> convertMatchToMap(MatchEntity match) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", match.getId());
        map.put("homeTeam", match.getHomeTeam().getName());
        map.put("awayTeam", match.getAwayTeam().getName());
        map.put("homeTeamEmblem", match.getHomeTeam().getEmblemUrl());
        map.put("awayTeamEmblem", match.getAwayTeam().getEmblemUrl());
        map.put("homeScore", match.getHomeScore());
        map.put("awayScore", match.getAwayScore());
        map.put("kickoffAt", match.getKickoffAt().toString());
        map.put("league", match.getLeague());
        map.put("matchday", match.getMatchday());
        map.put("status", match.getKickoffAt().isBefore(LocalDateTime.now()) ? "FINISHED" : "SCHEDULED");
        return map;
    }

    // ==================== API FETCHING METHODS ====================
    
    /**
     * Завантажити матчі для ліги з зовнішнього API
     */
    public List<Map<String, Object>> fetchMatchesFromApi(String leagueCode) {
        String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
        if (apiLeagueCode == null) {
            log.warn("Невідомий код ліги: {}", leagueCode);
            return new ArrayList<>();
        }

        try {
            log.info("→ Запит матчів з API для ліги: {} ({})", leagueCode, apiLeagueCode);

            rateLimiterService.acquire();

            Map<String, Object> response = footballApiWebClient
                    .get()
                    .uri("/competitions/{code}/matches", apiLeagueCode)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && response.containsKey("matches")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> matchesData = (List<Map<String, Object>>) response.get("matches");
                
                if (matchesData != null && !matchesData.isEmpty()) {
                    log.info("✅ Отримано {} матчів для ліги {}", matchesData.size(), leagueCode);
                    
                    // Конвертуємо дані API в наш формат
                    List<Map<String, Object>> convertedMatches = new ArrayList<>();
                    for (Map<String, Object> matchData : matchesData) {
                        Map<String, Object> converted = new HashMap<>();
                        
                        // Базова інформація
                        converted.put("id", matchData.get("id"));
                        converted.put("league", leagueCode);
                        converted.put("status", matchData.get("status"));
                        converted.put("matchday", matchData.get("matchday"));
                        
                        // Дата і час
                        String utcDate = (String) matchData.get("utcDate");
                        if (utcDate != null) {
                            converted.put("kickoffAt", utcDate);
                        }
                        
                        // Команди
                        @SuppressWarnings("unchecked")
                        Map<String, Object> homeTeamData = (Map<String, Object>) matchData.get("homeTeam");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> awayTeamData = (Map<String, Object>) matchData.get("awayTeam");
                        
                        if (homeTeamData != null) {
                            converted.put("homeTeam", homeTeamData.get("name"));
                            converted.put("homeTeamId", homeTeamData.get("id"));
                        }
                        if (awayTeamData != null) {
                            converted.put("awayTeam", awayTeamData.get("name"));
                            converted.put("awayTeamId", awayTeamData.get("id"));
                        }
                        
                        // Рахунок
                        @SuppressWarnings("unchecked")
                        Map<String, Object> scoreData = (Map<String, Object>) matchData.get("score");
                        if (scoreData != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> fullTime = (Map<String, Object>) scoreData.get("fullTime");
                            if (fullTime != null) {
                                converted.put("homeScore", fullTime.get("home"));
                                converted.put("awayScore", fullTime.get("away"));
                            }
                        }
                        
                        convertedMatches.add(converted);
                    }
                    
                    return convertedMatches;
                }
            }

            log.warn("⚠️ API не повернуло матчів для ліги {}", leagueCode);
            return new ArrayList<>();

        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.contains("429")) {
                log.warn("⚠️ HTTP 429: Перевищено ліміт запитів для матчів {}. Спробую ще раз через 60 секунд", leagueCode);
                try {
                    Thread.sleep(60000); // Чекаємо 60 секунд
                    return fetchMatchesFromApi(leagueCode); // Рекурсивний виклик для retry
                } catch (InterruptedException ie) {
                    log.error("❌ Перервано очікування retry для матчів {}: {}", leagueCode, ie.getMessage());
                }
            }
            log.error("❌ Помилка завантаження матчів з API для {}: {}", leagueCode, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Завантажити бомбардирів для ліги з зовнішнього API
     */
    public List<Map<String, Object>> fetchScorersFromApi(String leagueCode) {
        String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
        if (apiLeagueCode == null) {
            log.warn("Невідомий код ліги: {}", leagueCode);
            return new ArrayList<>();
        }

        try {
            log.info("→ Запит бомбардирів з API для ліги: {} ({})", leagueCode, apiLeagueCode);

            Map<String, Object> response = footballApiWebClient
                    .get()
                    .uri("/competitions/{code}/scorers", apiLeagueCode)
                    .retrieve()
                    .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            if (response != null && response.containsKey("scorers")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> scorersData = (List<Map<String, Object>>) response.get("scorers");
                
                if (scorersData != null && !scorersData.isEmpty()) {
                    log.info("✅ Отримано {} бомбардирів для ліги {}", scorersData.size(), leagueCode);
                    
                    // Конвертуємо дані API в наш формат
                    List<Map<String, Object>> convertedScorers = new ArrayList<>();
                    for (Map<String, Object> scorerData : scorersData) {
                        Map<String, Object> converted = new HashMap<>();
                        
                        // Інформація про гравця
                        @SuppressWarnings("unchecked")
                        Map<String, Object> playerData = (Map<String, Object>) scorerData.get("player");
                        if (playerData != null) {
                            converted.put("playerName", playerData.get("name"));
                            converted.put("playerId", playerData.get("id"));
                        }
                        
                        // Інформація про команду
                        @SuppressWarnings("unchecked")
                        Map<String, Object> teamData = (Map<String, Object>) scorerData.get("team");
                        if (teamData != null) {
                            converted.put("teamName", teamData.get("name"));
                            converted.put("teamId", teamData.get("id"));
                        }
                        
                        // Статистика
                        converted.put("goals", scorerData.get("goals"));
                        converted.put("assists", scorerData.get("assists"));
                        converted.put("penalties", scorerData.get("penalties"));
                        converted.put("league", leagueCode);
                        
                        convertedScorers.add(converted);
                    }
                    
                    return convertedScorers;
                }
            }

            log.warn("⚠️ API не повернуло бомбардирів для ліги {}", leagueCode);
            return new ArrayList<>();

        } catch (Exception e) {
            log.error("❌ Помилка завантаження бомбардирів з API для {}: {}", leagueCode, e.getMessage());
            return new ArrayList<>();
        }
    }
}
