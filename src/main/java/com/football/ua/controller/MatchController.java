package com.football.ua.controller;

import com.football.ua.exception.BadRequestException;
import com.football.ua.exception.NotFoundException;
import com.football.ua.model.Match;
import com.football.ua.model.entity.MatchEntity;
import com.football.ua.model.entity.TeamEntity;
import com.football.ua.service.MatchDbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@Tag(name = "Matches", description = "API для управління футбольними матчами")
public class MatchController {
    
    private static final Logger log = LoggerFactory.getLogger(MatchController.class);
    private static final Marker DB_OPERATION = MarkerFactory.getMarker("DB_OPERATION");
    private static final Marker API_CALL = MarkerFactory.getMarker("API_CALL");
    private static final Marker CRUD_CREATE = MarkerFactory.getMarker("CRUD_CREATE");
    private static final Marker CRUD_UPDATE = MarkerFactory.getMarker("CRUD_UPDATE");
    private static final Marker CRUD_DELETE = MarkerFactory.getMarker("CRUD_DELETE");
    
    private final MatchDbService matchDbService;
    private final com.football.ua.service.ActivityLogService activityLogService;

    public MatchController(MatchDbService matchDbService, com.football.ua.service.ActivityLogService activityLogService) {
        this.matchDbService = matchDbService;
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @Operation(summary = "Отримати всі матчі", description = "Повертає список всіх футбольних матчів")
    public List<Match> list() {
        MDC.put("operation", "list");
        MDC.put("endpoint", "/api/matches");
        try {
            log.info(DB_OPERATION, "Запит на отримання списку матчів");
            List<Match> matches = matchDbService.list().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
            log.info(DB_OPERATION, "Повернуто {} матчів", matches.size());
            return matches;
        } finally {
            MDC.clear();
        }
    }

    @GetMapping("/{id}")
    public Match one(@PathVariable Long id) {
        MDC.put("operation", "getById");
        MDC.put("matchId", String.valueOf(id));
        MDC.put("endpoint", "/api/matches/" + id);
        try {
            log.info(DB_OPERATION, "Пошук матчу з ID: {}", id);
            var entities = matchDbService.list();
            var entity = entities.stream()
                .filter(m -> m.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Match not found"));
            String homeTeam = entity.getHomeTeam() != null ? entity.getHomeTeam().getName() : "TBD";
            String awayTeam = entity.getAwayTeam() != null ? entity.getAwayTeam().getName() : "TBD";
            log.info(DB_OPERATION, "Матч знайдено: {} vs {}", homeTeam, awayTeam);
            return toDto(entity);
        } finally {
            MDC.clear();
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('EDITOR')")
    @Operation(summary = "Створити новий матч", description = "Створює новий футбольний матч")
    public Match create(@Valid @RequestBody Match body) {
        MDC.put("operation", "create");
        MDC.put("homeTeam", body.homeTeam);
        MDC.put("awayTeam", body.awayTeam);
        MDC.put("endpoint", "/api/matches");
        try {
            log.info(CRUD_CREATE, "Створення нового матчу: {} vs {}", body.homeTeam, body.awayTeam);
            MatchEntity entity = matchDbService.create(
                body.homeTeam,
                body.awayTeam,
                body.kickoffAt
            );
            MDC.put("matchId", String.valueOf(entity.getId()));
            log.info(CRUD_CREATE, "Матч успішно створено з ID: {}", entity.getId());
            
            activityLogService.logActivity(
                "Додано новий матч",
                String.format("%s vs %s", body.homeTeam, body.awayTeam),
                "MATCHES"
            );
            
            return toDto(entity);
        } finally {
            MDC.clear();
        }
    }

    @PatchMapping("/{id}/score")
    @PreAuthorize("hasRole('EDITOR')")
    public Match updateScore(@PathVariable Long id, @RequestBody Map<String,Integer> body) {
        MDC.put("operation", "updateScore");
        MDC.put("matchId", String.valueOf(id));
        MDC.put("endpoint", "/api/matches/" + id + "/score");
        try {
            Integer homeScore = body.get("homeScore");
            Integer awayScore = body.get("awayScore");
            MDC.put("score", homeScore + ":" + awayScore);
            log.info(CRUD_UPDATE, "Оновлення рахунку матчу ID {}: {}:{}", id, homeScore, awayScore);
            MatchEntity entity = matchDbService.updateScore(id, homeScore, awayScore);
            log.info(CRUD_UPDATE, "Рахунок успішно оновлено");
            
            activityLogService.logActivity(
                "Оновлено рахунок матчу",
                String.format("Рахунок %d:%d", homeScore, awayScore),
                "MATCHES"
            );
            
            return toDto(entity);
        } finally {
            MDC.clear();
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('EDITOR')")
    public void delete(@PathVariable Long id) {
        MDC.put("operation", "delete");
        MDC.put("matchId", String.valueOf(id));
        MDC.put("endpoint", "/api/matches/" + id);
        try {
            log.warn(CRUD_DELETE, "Видалення матчу з ID: {}", id);
            matchDbService.delete(id);
            log.warn(CRUD_DELETE, "Матч успішно видалено");
            
            activityLogService.logActivity(
                "Видалено матч",
                String.format("Матч #%d видалено", id),
                "MATCHES"
            );
        } finally {
            MDC.clear();
        }
    }
    
    private Match toDto(MatchEntity entity) {
        Match dto = new Match();
        dto.id = entity.getId();
        dto.kickoffAt = entity.getKickoffAt();
        dto.homeScore = entity.getHomeScore();
        dto.awayScore = entity.getAwayScore();

        dto.homeTeam = entity.getHomeTeam() != null ? entity.getHomeTeam().getName() : "TBD";
        dto.awayTeam = entity.getAwayTeam() != null ? entity.getAwayTeam().getName() : "TBD";
        
        log.debug("Матч {}: home={}, away={}", entity.getId(), dto.homeTeam, dto.awayTeam);

        return dto;
    }

    @GetMapping("/teams/info")
    public Map<String,Object> teamInfo(@RequestParam String name) {
        MDC.put("operation", "externalAPI");
        MDC.put("teamName", name);
        MDC.put("endpoint", "/api/matches/teams/info");
        try {
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8);
            String url = "https://en.wikipedia.org/api/rest_v1/page/summary/" + encoded;
            MDC.put("externalUrl", url);

            log.info(API_CALL, "Запит до Wikipedia API для команди: {}", name);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "FootballApp/1.0 (localhost; coursework)");
            headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> req = new HttpEntity<>(headers);

            try {
                @SuppressWarnings("unchecked")
                ResponseEntity<Map<String,Object>> resp = (ResponseEntity<Map<String,Object>>) (ResponseEntity<?>) new RestTemplate().exchange(url, HttpMethod.GET, req, Map.class);
                if (resp.getBody() == null) throw new BadRequestException("External API returned empty body");
                log.info(API_CALL, "Отримано відповідь від Wikipedia API, статус: {}", resp.getStatusCode());
                return resp.getBody();
            } catch (HttpClientErrorException.NotFound e) {
                log.error(API_CALL, "Сторінка команди не знайдена: {}", name);
                throw new NotFoundException("Team page not found: " + name);
            } catch (RestClientException e) {
                log.error(API_CALL, "Помилка зовнішнього API: {}", e.getClass().getSimpleName());
                throw new BadRequestException("External API error: " + e.getClass().getSimpleName());
            }
        } finally {
            MDC.clear();
        }
    }
}


