package com.football.ua.controller;

import com.football.ua.model.Team;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamController {
    private static final Map<Long, Team> db = new LinkedHashMap<>();
    private static long idSeq = 1;

    @GetMapping
    public List<Team> list() { return new ArrayList<>(db.values()); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Team create(@RequestBody Team body) {
        body.id = idSeq++;
        db.put(body.id, body);
        return body;
    }
    
    @GetMapping("/actual")
    public Map<String, List<Team>> getActualTeams() {
        Map<String, List<Team>> leagues = new LinkedHashMap<>();
        
        List<Team> upl = Arrays.asList(
            createTeam("Динамо Київ", "UPL", "Київ", "🔵⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/2/23/FC_Dynamo_Kyiv_logo.svg/100px-FC_Dynamo_Kyiv_logo.svg.png"),
            createTeam("Шахтар Донецьк", "UPL", "Донецьк", "🟠⚫", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a1/FC_Shakhtar_Donetsk.svg/100px-FC_Shakhtar_Donetsk.svg.png"),
            createTeam("Дніпро-1", "UPL", "Дніпро", "🔵⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f8/FC_Dnipro-1_logo.svg/100px-FC_Dnipro-1_logo.svg.png"),
            createTeam("Ворскла", "UPL", "Полтава", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/8/82/FC_Vorskla_Poltava_logo.svg/100px-FC_Vorskla_Poltava_logo.svg.png"),
            createTeam("Зоря", "UPL", "Луганськ", "⚫⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/4/4c/FC_Zorya_Luhansk_logo.svg/100px-FC_Zorya_Luhansk_logo.svg.png"),
            createTeam("Олександрія", "UPL", "Олександрія", "🟡🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/7/79/FC_Olexandriya_logo.svg/100px-FC_Olexandriya_logo.svg.png"),
            createTeam("Колос", "UPL", "Ковалівка", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/5a/FC_Kolos_Kovalivka_logo.svg/100px-FC_Kolos_Kovalivka_logo.svg.png"),
            createTeam("Рух", "UPL", "Львів", "🟡🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/4/4f/FC_Rukh_Lviv_logo.svg/100px-FC_Rukh_Lviv_logo.svg.png"),
            createTeam("Кривбас", "UPL", "Кривий Ріг", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/7/79/FC_Kryvbas_Kryvyi_Rih_logo.svg/100px-FC_Kryvbas_Kryvyi_Rih_logo.svg.png"),
            createTeam("Минай", "UPL", "Мінай", "🔴🟡", "https://upload.wikimedia.org/wikipedia/en/thumb/c/c7/FC_Mynai_logo.svg/100px-FC_Mynai_logo.svg.png"),
            createTeam("Чорноморець", "UPL", "Одеса", "🔵⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/6/60/FC_Chornomorets_Odesa_logo.svg/100px-FC_Chornomorets_Odesa_logo.svg.png"),
            createTeam("Металіст 1925", "UPL", "Харків", "🟡🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/8/8f/FC_Metalist_1925_Kharkiv_logo.svg/100px-FC_Metalist_1925_Kharkiv_logo.svg.png"),
            createTeam("Верес", "UPL", "Рівне", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a7/FC_Veres_Rivne_logo.svg/100px-FC_Veres_Rivne_logo.svg.png"),
            createTeam("Інгулець", "UPL", "Петрове", "🟡⚫", "https://upload.wikimedia.org/wikipedia/en/thumb/6/6c/FC_Inhulets_Petrove_logo.svg/100px-FC_Inhulets_Petrove_logo.svg.png"),
            createTeam("ЛНЗ", "UPL", "Черкаси", "🔵🟡", "https://upload.wikimedia.org/wikipedia/en/thumb/f/fd/FC_LNZ_Cherkasy_logo.svg/100px-FC_LNZ_Cherkasy_logo.svg.png"),
            createTeam("Полісся", "UPL", "Житомир", "🟢⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/0/0e/FC_Polissya_Zhytomyr_logo.svg/100px-FC_Polissya_Zhytomyr_logo.svg.png")
        );
        
        List<Team> ucl = Arrays.asList(
            createTeam("Реал Мадрид", "UCL", "Мадрид", "⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/56/Real_Madrid_CF.svg/100px-Real_Madrid_CF.svg.png"),
            createTeam("Манчестер Сіті", "UCL", "Манчестер", "🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/e/eb/Manchester_City_FC_badge.svg/100px-Manchester_City_FC_badge.svg.png"),
            createTeam("Баварія", "UCL", "Мюнхен", "🔴⚪", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/FC_Bayern_M%C3%BCnchen_logo_%282017%29.svg/100px-FC_Bayern_M%C3%BCnchen_logo_%282017%29.svg.png"),
            createTeam("ПСЖ", "UCL", "Париж", "🔵🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a7/Paris_Saint-Germain_F.C..svg/100px-Paris_Saint-Germain_F.C..svg.png"),
            createTeam("Інтер", "UCL", "Мілан", "🔵⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/05/FC_Internazionale_Milano_2021.svg/100px-FC_Internazionale_Milano_2021.svg.png"),
            createTeam("Барселона", "UCL", "Барселона", "🔴🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/4/47/FC_Barcelona_%28crest%29.svg/100px-FC_Barcelona_%28crest%29.svg.png"),
            createTeam("Арсенал", "UCL", "Лондон", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/53/Arsenal_FC.svg/100px-Arsenal_FC.svg.png"),
            createTeam("Атлетіко", "UCL", "Мадрид", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/f/f4/Atletico_Madrid_2017_logo.svg/100px-Atletico_Madrid_2017_logo.svg.png"),
            createTeam("Ліверпуль", "UCL", "Ліверпуль", "🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/100px-Liverpool_FC.svg.png"),
            createTeam("Боруссія Д", "UCL", "Дортмунд", "🟡⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/6/67/Borussia_Dortmund_logo.svg/100px-Borussia_Dortmund_logo.svg.png"),
            createTeam("Ювентус", "UCL", "Турин", "⚪⚫", "https://upload.wikimedia.org/wikipedia/commons/thumb/b/b6/Juventus_FC_2017_logo.svg/100px-Juventus_FC_2017_logo.svg.png"),
            createTeam("Бенфіка", "UCL", "Лісабон", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/a/a2/SL_Benfica_logo.svg/100px-SL_Benfica_logo.svg.png")
        );
        
        List<Team> epl = Arrays.asList(
            createTeam("Манчестер Сіті", "EPL", "Манчестер", "🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/e/eb/Manchester_City_FC_badge.svg/100px-Manchester_City_FC_badge.svg.png"),
            createTeam("Арсенал", "EPL", "Лондон", "🔴⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/53/Arsenal_FC.svg/100px-Arsenal_FC.svg.png"),
            createTeam("Ліверпуль", "EPL", "Ліверпуль", "🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/100px-Liverpool_FC.svg.png"),
            createTeam("Астон Вілла", "EPL", "Бірмінгем", "🟣", "https://upload.wikimedia.org/wikipedia/en/thumb/f/f9/Aston_Villa_FC_crest_%282016%29.svg/100px-Aston_Villa_FC_crest_%282016%29.svg.png"),
            createTeam("Тоттенгем", "EPL", "Лондон", "⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/b/b4/Tottenham_Hotspur.svg/100px-Tottenham_Hotspur.svg.png"),
            createTeam("Челсі", "EPL", "Лондон", "🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/c/cc/Chelsea_FC.svg/100px-Chelsea_FC.svg.png"),
            createTeam("Ньюкасл", "EPL", "Ньюкасл", "⚫⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/5/56/Newcastle_United_Logo.svg/100px-Newcastle_United_Logo.svg.png"),
            createTeam("Манчестер Юнайтед", "EPL", "Манчестер", "🔴", "https://upload.wikimedia.org/wikipedia/en/thumb/7/7a/Manchester_United_FC_crest.svg/100px-Manchester_United_FC_crest.svg.png"),
            createTeam("Вест Хем", "EPL", "Лондон", "🟣🔵", "https://upload.wikimedia.org/wikipedia/en/thumb/c/c2/West_Ham_United_FC_logo.svg/100px-West_Ham_United_FC_logo.svg.png"),
            createTeam("Брайтон", "EPL", "Брайтон", "🔵⚪", "https://upload.wikimedia.org/wikipedia/en/thumb/f/fd/Brighton_%26_Hove_Albion_logo.svg/100px-Brighton_%26_Hove_Albion_logo.svg.png"),
            createTeam("Вулверхемптон", "EPL", "Вулверхемптон", "🟠⚫", "https://upload.wikimedia.org/wikipedia/en/thumb/f/fc/Wolverhampton_Wanderers.svg/100px-Wolverhampton_Wanderers.svg.png"),
            createTeam("Фулгем", "EPL", "Лондон", "⚪⚫", "https://upload.wikimedia.org/wikipedia/en/thumb/e/eb/Fulham_FC_%28shield%29.svg/100px-Fulham_FC_%28shield%29.svg.png")
        );
        
        leagues.put("UPL", upl);
        leagues.put("UCL", ucl);
        leagues.put("EPL", epl);
        
        return leagues;
    }
    
    @GetMapping("/leagues")
    public List<String> getLeagues() {
        return Arrays.asList("UPL", "UCL", "EPL", "LaLiga", "Bundesliga", "SerieA", "Ligue1");
    }
    
    private Team createTeam(String name, String league, String city, String colors, String emblemUrl) {
        Team team = new Team();
        team.name = name;
        team.league = league;
        team.city = city;
        team.colors = colors;
        team.emblemUrl = emblemUrl;
        return team;
    }
}

