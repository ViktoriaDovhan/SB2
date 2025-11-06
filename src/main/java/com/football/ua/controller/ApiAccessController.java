package com.football.ua.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

@RestController
@RequestMapping("/api/access")
@Tag(name = "API Access Info", description = "Інформація про доступні API endpoints")
public class ApiAccessController {

    @GetMapping("/info")
    @Operation(summary = "Отримати інформацію про доступні API", 
               description = "Повертає список доступних endpoint'ів в залежності від ролі користувача")
    public Map<String, Object> getAccessInfo(Authentication authentication) {
        Map<String, Object> response = new LinkedHashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            response.put("authenticated", true);
            response.put("username", authentication.getName());
            response.put("roles", authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        } else {
            response.put("authenticated", false);
            response.put("username", "anonymous");
            response.put("roles", List.of());
        }

        response.put("availableEndpoints", getAvailableEndpoints(authentication));
        
        return response;
    }

    @GetMapping("/endpoints")
    @Operation(summary = "Отримати всі endpoint'и з правами доступу",
               description = "Повертає повний список endpoint'ів згруповані за категоріями доступу")
    public Map<String, List<EndpointInfo>> getAllEndpoints() {
        Map<String, List<EndpointInfo>> endpoints = new LinkedHashMap<>();

        endpoints.put("PUBLIC", List.of(
            new EndpointInfo("GET", "/api/news", "Перегляд новин", null),
            new EndpointInfo("GET", "/api/news/{id}", "Перегляд конкретної новини", null),
            new EndpointInfo("GET", "/api/matches", "Перегляд матчів", null),
            new EndpointInfo("GET", "/api/matches/{id}", "Перегляд конкретного матчу", null),
            new EndpointInfo("GET", "/api/teams", "Перегляд команд", null),
            new EndpointInfo("GET", "/api/teams/{id}", "Перегляд конкретної команди", null),
            new EndpointInfo("GET", "/api/forum/topics", "Перегляд тем форуму", null),
            new EndpointInfo("GET", "/api/forum/topics/{id}/posts", "Перегляд постів теми", null),
            new EndpointInfo("POST", "/api/auth/register", "Реєстрація користувача", null),
            new EndpointInfo("POST", "/api/auth/login", "Вхід користувача", null)
        ));

        endpoints.put("AUTHENTICATED", List.of(
            new EndpointInfo("POST", "/api/forum/topics", "Створення теми форуму", "USER, MODERATOR, EDITOR"),
            new EndpointInfo("POST", "/api/forum/topics/{id}/posts", "Додавання коментаря", "USER, MODERATOR, EDITOR"),
            new EndpointInfo("POST", "/api/news/{id}/like", "Вподобати новину", "USER, MODERATOR, EDITOR"),
            new EndpointInfo("DELETE", "/api/forum/topics/{id}", "Видалення своєї теми", "власник або MODERATOR"),
            new EndpointInfo("DELETE", "/api/forum/topics/{topicId}/posts/{postId}", "Видалення свого поста", "власник або MODERATOR")
        ));

        endpoints.put("MODERATOR", List.of(
            new EndpointInfo("POST", "/api/moderator/users/{username}/ban", "Блокування користувача", "MODERATOR"),
            new EndpointInfo("POST", "/api/moderator/users/{username}/unban", "Розблокування користувача", "MODERATOR"),
            new EndpointInfo("DELETE", "/api/forum/topics/{id}", "Видалення будь-якої теми", "MODERATOR"),
            new EndpointInfo("DELETE", "/api/forum/topics/{topicId}/posts/{postId}", "Видалення будь-якого поста", "MODERATOR")
        ));

        endpoints.put("EDITOR", List.of(
            new EndpointInfo("POST", "/api/news", "Створення новини", "EDITOR"),
            new EndpointInfo("PUT", "/api/news/{id}", "Редагування новини", "EDITOR"),
            new EndpointInfo("DELETE", "/api/news/{id}", "Видалення новини", "EDITOR"),
            new EndpointInfo("POST", "/api/matches", "Створення матчу", "EDITOR"),
            new EndpointInfo("PATCH", "/api/matches/{id}/score", "Оновлення рахунку матчу", "EDITOR"),
            new EndpointInfo("DELETE", "/api/matches/{id}", "Видалення матчу", "EDITOR"),
            new EndpointInfo("POST", "/api/teams", "Створення команди", "EDITOR"),
            new EndpointInfo("PUT", "/api/teams/{id}", "Оновлення команди", "EDITOR"),
            new EndpointInfo("DELETE", "/api/teams/{id}", "Видалення команди", "EDITOR")
        ));
        
        return endpoints;
    }

    private List<String> getAvailableEndpoints(Authentication authentication) {
        List<String> endpoints = new ArrayList<>();

        endpoints.addAll(List.of(
            "GET /api/news - Перегляд новин",
            "GET /api/matches - Перегляд матчів",
            "GET /api/teams - Перегляд команд",
            "GET /api/forum/topics - Перегляд форуму"
        ));
        
        if (authentication != null && authentication.isAuthenticated()) {
            endpoints.addAll(List.of(
                "POST /api/forum/topics - Створення теми",
                "POST /api/forum/topics/{id}/posts - Коментування",
                "POST /api/news/{id}/like - Вподобайки",
                "DELETE /api/forum/topics/{id} - Видалення своєї теми"
            ));
            
            Set<String> roles = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(role -> role.replace("ROLE_", ""))
                .collect(java.util.stream.Collectors.toSet(            ));

            if (roles.contains("MODERATOR")) {
                endpoints.addAll(List.of(
                    "POST /api/moderator/users/{username}/ban - Блокування користувачів",
                    "DELETE /api/forum/* - Видалення будь-якого контенту на форумі"
                ));
            }

            if (roles.contains("EDITOR")) {
                endpoints.addAll(List.of(
                    "POST /api/news - Створення новин",
                    "PUT /api/news/{id} - Редагування новин",
                    "DELETE /api/news/{id} - Видалення новин",
                    "POST /api/matches - Створення матчів",
                    "PATCH /api/matches/{id}/score - Оновлення рахунку"
                ));
            }
        }
        
        return endpoints;
    }

    public record EndpointInfo(String method, String path, String description, String requiredRole) {}
}

