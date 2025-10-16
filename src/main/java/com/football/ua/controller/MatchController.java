package com.football.ua.controller;

import com.football.ua.exception.BadRequestException;
import com.football.ua.exception.NotFoundException;
import com.football.ua.model.Match;
import com.football.ua.model.entity.MatchEntity;
import com.football.ua.model.entity.TeamEntity;
import com.football.ua.service.MatchDbService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import jakarta.validation.Valid;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.*;
import org.springframework.web.client.*;

import java.util.Map;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/matches")
public class MatchController {
    
    private final MatchDbService matchDbService;

    public MatchController(MatchDbService matchDbService) {
        this.matchDbService = matchDbService;
    }

    @GetMapping
    public List<Match> list() { 
        return matchDbService.list().stream()
            .map(this::toDto)
            .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    public Match one(@PathVariable Long id) {
        var entities = matchDbService.list();
        var entity = entities.stream()
            .filter(m -> m.getId().equals(id))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("Match not found"));
        return toDto(entity);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Match create(@Valid @RequestBody Match body) {
        MatchEntity entity = matchDbService.create(
            body.homeTeam,
            body.awayTeam,
            body.kickoffAt
        );
        return toDto(entity);
    }

    @PatchMapping("/{id}/score")
    public Match updateScore(@PathVariable Long id, @RequestBody Map<String,Integer> body) {
        Integer homeScore = body.get("homeScore");
        Integer awayScore = body.get("awayScore");
        MatchEntity entity = matchDbService.updateScore(id, homeScore, awayScore);
        return toDto(entity);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        matchDbService.delete(id);
    }
    
    private Match toDto(MatchEntity entity) {
        Match dto = new Match();
        dto.id = entity.getId();
        dto.kickoffAt = entity.getKickoffAt();
        dto.homeScore = entity.getHomeScore();
        dto.awayScore = entity.getAwayScore();
        
        List<String> teamNames = entity.getTeams().stream()
            .map(TeamEntity::getName)
            .collect(Collectors.toList());
        
        dto.homeTeam = teamNames.size() > 0 ? teamNames.get(0) : "Команда 1";
        dto.awayTeam = teamNames.size() > 1 ? teamNames.get(1) : "Команда 2";
        
        return dto;
    }

    @GetMapping("/teams/info")
    public Map<String,Object> teamInfo(@RequestParam String name) {
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
        String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encoded;

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "FootballApp/1.0 (localhost; coursework)");
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        HttpEntity<Void> req = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> resp = new RestTemplate().exchange(url, HttpMethod.GET, req, Map.class);
            if (resp.getBody() == null) throw new BadRequestException("External API returned empty body");
            return (Map<String,Object>) resp.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new NotFoundException("Team page not found: " + name);
        } catch (RestClientException e) {
            throw new BadRequestException("External API error: " + e.getClass().getSimpleName());
        }
    }
}


