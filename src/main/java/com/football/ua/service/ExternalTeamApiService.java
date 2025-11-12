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

    @Value("${football.api.enabled:false}")
    private boolean apiEnabled;

    private Map<String, List<Team>> cachedTeams = null;
    private long lastUpdateTime = 0;
    private static final long CACHE_DURATION = 5 * 60 * 1000;
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

    private static final Map<String, String> LEAGUE_COLORS = Map.of(
            "UPL", "🔵🟡",
            "EPL", "🔵⚪",
            "UCL", "⭐🔵",
            "LaLiga", "🔴🟡",
            "Bundesliga", "🔴⚫",
            "SerieA", "🔵⚪",
            "Ligue1", "🔵🔴"
    );

    public synchronized Map<String, List<Team>> getTeamsFromApi() {
        // Спочатку перевіряємо файловий кеш
        String cacheKey = "all_teams";
        log.debug("🔍 Перевірка кешу команд: {}/{}", "teams", cacheKey);

        boolean cacheValid = fileCacheService.isCacheValid("teams", cacheKey);
        log.info("🔍 Результат перевірки кешу команд: {}", cacheValid);

        if (cacheValid) {
            try {
                Object cached = fileCacheService.loadFromCache("teams", cacheKey, Map.class);
                if (cached != null) {
                    log.info("📦 Повертаємо закешовані команди з файлу");
                    return (Map<String, List<Team>>) cached;
                } else {
                    log.warn("⚠️ Кеш файл існує але дані порожні");
                }
            } catch (Exception e) {
                log.warn("⚠️ Помилка завантаження команд з кешу: {}", e.getMessage());
            }
        } else {
            log.info("⏰ Кеш команд застарілий або не існує (cacheValid={})", cacheValid);
        }

        if (!apiEnabled) {
            log.info("API вимкнено, використовуємо локальні дані");
            Map<String, List<Team>> fallbackTeams = getFallbackTeams();
            fileCacheService.saveToCache("teams", cacheKey, fallbackTeams);
            return fallbackTeams;
        }

        log.info("⚡ Завантажуємо свіжі дані команд з API...");

        try {
            Map<String, List<Team>> allLeagues = new LinkedHashMap<>();

            allLeagues.put("UPL", getFallbackTeamsForLeague("UPL"));
            log.info("✅ UPL: 16 команд (локально)");

            List<String> apiLeagues = Arrays.asList("UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");

            for (int i = 0; i < apiLeagues.size(); i++) {
                String leagueCode = apiLeagues.get(i);
                try {
                    if (i > 0) {
                        Thread.sleep(3000 + (i * 1000)); // Затримка 3-8 секунд між запитами
                    }

                    List<Team> teams = fetchTeamsForLeague(leagueCode);
                    if (!teams.isEmpty()) {
                        allLeagues.put(leagueCode, teams);
                        log.info("✅ {}: {} команд (з API)", leagueCode, teams.size());
                    } else {
                        log.warn("⚠️ {}: порожня відповідь, використовуємо локальні дані", leagueCode);
                        allLeagues.put(leagueCode, getFallbackTeamsForLeague(leagueCode));
                    }
                } catch (Exception e) {
                    log.error("❌ {}: помилка API - {}. Використовуємо локальні дані",
                             leagueCode, e.getMessage());
                    allLeagues.put(leagueCode, getFallbackTeamsForLeague(leagueCode));
                }
            }

            // Зберігаємо в файловий кеш
            fileCacheService.saveToCache("teams", cacheKey, allLeagues);

            int totalTeams = allLeagues.values().stream().mapToInt(List::size).sum();
            log.info("🎯 Завантажено {} ліг, {} команд. Закешовано", allLeagues.size(), totalTeams);
            return allLeagues;

        } catch (Exception e) {
            log.error("❌ Критична помилка: {}. Використовуємо локальні дані", e.getMessage());
            Map<String, List<Team>> fallbackTeams = getFallbackTeams();
            fileCacheService.saveToCache("teams", "all_teams", fallbackTeams);
            return fallbackTeams;
        }
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
        // Спочатку перевіряємо файловий кеш
        String cacheKey = leagueCode.toLowerCase();
        if (fileCacheService.isCacheValid("standings", cacheKey)) {
            try {
                Object cached = fileCacheService.loadFromCache("standings", cacheKey, Map.class);
                if (cached != null) {
                    log.debug("📦 Повертаємо закешовану турнірну таблицю для {} з файлу", leagueCode);
                    return (Map<String, Object>) cached;
                }
            } catch (Exception e) {
                log.warn("⚠️ Помилка завантаження турнірної таблиці з кешу: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();

        if ("UPL".equals(leagueCode)) {
            result.put("league", "UPL");
            result.put("standings", new ArrayList<>());
            result.put("source", "local");
            fileCacheService.saveToCache("standings", cacheKey, result);
            return result;
        }

        if (!apiEnabled) {
            result.put("league", leagueCode);
            result.put("standings", new ArrayList<>());
            result.put("source", "local");
            fileCacheService.saveToCache("standings", cacheKey, result);
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

                        // Зберігаємо в файловий кеш
                        fileCacheService.saveToCache("standings", cacheKey, result);

                        log.info("✅ Отримано турнірну таблицю для {} ({} команд)", leagueCode, formattedTable.size());
                        return result;
                    }
                }
            }

            throw new RuntimeException("Порожня відповідь від API");

        } catch (Exception e) {
            log.error("❌ Помилка отримання турнірної таблиці для {}: {}", leagueCode, e.getMessage());

            result.put("league", leagueCode);
            result.put("standings", new ArrayList<>());
            result.put("source", "error");
            result.put("error", e.getMessage());

            // Зберігаємо помилку в кеш на короткий термін (5 хвилин)
            fileCacheService.saveToCache("standings", cacheKey, result, 5);

            return result;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getUpcomingMatches() {
        return getMatchesByMatchday("upcoming", 0, "SCHEDULED");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getPreviousMatches() {
        return getMatchesByMatchday("previous", -1, "FINISHED");
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getMatchesByMatchday(String type, int matchdayOffset, String statusFilter) {
        String cacheKey = type + "_matches_by_matchday";

        // Перевіряємо файловий кеш з категорією
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
            fileCacheService.saveToCache("matches", cacheKey, empty);
            return empty;
        }

        log.info("⚡ Завантажуємо {} матчі по турах...", type);
        
        try {
            List<Map<String, Object>> allMatches = new ArrayList<>();
            List<String> apiLeagues = Arrays.asList("UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");
            
            boolean hasConnectionIssue = false;
            
            for (int i = 0; i < apiLeagues.size(); i++) {
                String leagueCode = apiLeagues.get(i);
                String apiLeagueCode = LEAGUE_CODES.get(leagueCode);
                
                if (apiLeagueCode == null) continue;
                
                // Якщо були проблеми з'єднання, пропускаємо решту
                if (hasConnectionIssue) {
                    log.warn("⚠️ {}: пропущено через проблеми з'єднання", leagueCode);
                    continue;
                }
                
                try {
                    if (i > 0) {
                        Thread.sleep(2000); // Збільшено затримку до 2 секунд
                    }
                    
                    // Спочатку отримуємо поточний тур з standings
                    Integer currentMatchday = getCurrentMatchday(apiLeagueCode);
                    if (currentMatchday == null) {
                        log.warn("⚠️ {}: не вдалося визначити поточний тур", leagueCode);
                        // Перевіряємо чи це проблема з'єднання
                        continue;
                    }
                    
                    int targetMatchday = currentMatchday + matchdayOffset;
                    if (targetMatchday < 1) {
                        log.debug("⚠️ {}: тур {} менше 1, пропускаємо", leagueCode, targetMatchday);
                        continue;
                    }
                    
                    log.debug("→ Запит: GET /competitions/{}/matches (тур {}, статус: {})", 
                             apiLeagueCode, targetMatchday, statusFilter);
                    
                    FootballDataResponse response = footballApiWebClient
                            .get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/competitions/{code}/matches")
                                    .queryParam("matchday", targetMatchday)
                                    .queryParam("status", statusFilter)
                                    .build(apiLeagueCode))
                            .retrieve()
                            .bodyToMono(FootballDataResponse.class)
                            .doOnError(error -> log.error("Помилка API для {}: {}", 
                                                         leagueCode, error.getMessage()))
                            .block();

                    if (response != null && response.getMatches() != null) {
                        List<Map<String, Object>> matches = response.getMatches().stream()
                                .map(match -> convertMatchToMap(match, leagueCode))
                                .collect(Collectors.toList());
                        
                        allMatches.addAll(matches);
                        log.info("✅ {}: {} матчів (тур {})", leagueCode, matches.size(), targetMatchday);
                    } else {
                        log.warn("⚠️ {}: порожня відповідь (тур {})", leagueCode, targetMatchday);
                    }
                    
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    log.error("❌ {}: помилка - {}", leagueCode, errorMsg);
                    
                    // Якщо це проблема з'єднання (DNS, timeout), припиняємо спроби
                    if (errorMsg != null && (errorMsg.contains("Failed to resolve") || 
                                             errorMsg.contains("Connection refused") ||
                                             errorMsg.contains("timeout"))) {
                        log.error("🌐 Виявлено проблему з'єднання. Припиняємо спроби для інших ліг.");
                        hasConnectionIssue = true;
                    }
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

            // Зберігаємо у файловий кеш на 30 хвилин
            fileCacheService.saveToCache("matches", cacheKey, allMatches);
            
            log.info("🎯 Завантажено {} {} матчів. Збережено в файловий кеш на 30 хв", 
                    allMatches.size(), type);
            return allMatches;
            
        } catch (Exception e) {
            log.error("❌ Критична помилка завантаження матчів: {}", e.getMessage());

            // Спробуємо повернути існуючі дані з кешу, якщо вони є
            try {
                Object cachedData = fileCacheService.loadFromCache("matches", cacheKey, List.class);
                if (cachedData != null && !((List<?>) cachedData).isEmpty()) {
                    log.info("📦 Повертаємо застарілі дані з кешу замість порожнього результату");
                    return (List<Map<String, Object>>) cachedData;
                }
            } catch (Exception cacheError) {
                log.warn("⚠️ Помилка читання з кешу: {}", cacheError.getMessage());
            }

            // Якщо кеш порожній або застарілий, повертаємо порожній список але НЕ кешуємо його
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
}

