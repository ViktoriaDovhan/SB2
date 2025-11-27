package com.football.ua.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.football.ua.model.Team;
import com.football.ua.model.entity.PostEntity;
import com.football.ua.model.entity.TopicEntity;
import com.football.ua.model.entity.UserEntity;
import com.football.ua.repo.UserRepository;
import com.football.ua.service.ExternalTeamApiService;
import com.football.ua.service.ForumDbService;
import com.football.ua.service.ModerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api/moderator")
@PreAuthorize("hasRole('MODERATOR')")
@Tag(name = "👮 Moderation", description = "API для модерації (MODERATOR)")
public class ModeratorController {

    private static final Logger log = LoggerFactory.getLogger(ModeratorController.class);

    private final ObjectMapper objectMapper;
    private final Path resourcesPath;
    private final ForumDbService forum;
    private final ObjectProvider<ModerationService> moderationProvider;
    private final UserRepository userRepository;
    private final ExternalTeamApiService externalTeamApiService;

    public ModeratorController(ObjectMapper objectMapper,
                              ForumDbService forum,
                              ObjectProvider<ModerationService> moderationProvider,
                              UserRepository userRepository,
                              ExternalTeamApiService externalTeamApiService) throws IOException {
        this.objectMapper = objectMapper;
        this.forum = forum;
        this.moderationProvider = moderationProvider;
        this.userRepository = userRepository;
        this.externalTeamApiService = externalTeamApiService;
        this.resourcesPath = getPathToResources();
            System.out.println("✅ Шлях для запису файлу гравця тижня: " + resourcesPath);
    }

    @PostMapping("/player-of-the-week")
    public ResponseEntity<String> setPlayerOfTheWeek(@RequestBody Map<String, String> playerData) {
        try {
            Path playerFilePath = resourcesPath.resolve("player-of-the-week.json");
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(playerData);
            Files.writeString(playerFilePath, jsonContent);

            return ResponseEntity.ok("Гравець тижня успішно оновлений. Перезапустіть додаток, щоб побачити зміни.");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Помилка при записі файлу: " + e.getMessage());
        }
    }


    @DeleteMapping("/player-of-the-week")
    public ResponseEntity<String> deletePlayerOfTheWeek() {
        try {
            Path playerFilePath = resourcesPath.resolve("player-of-the-week.json");

            if (Files.exists(playerFilePath)) {
                Files.delete(playerFilePath);
                return ResponseEntity.ok("Гравець тижня видалений. Перезапустіть додаток, щоб зміни вступили в силу.");
            }
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Помилка при видаленні файлу: " + e.getMessage());
        }
    }

    @PostMapping(value = "/preview", consumes = MediaType.TEXT_PLAIN_VALUE)
    public String preview(@RequestBody String text) {
        var m = moderationProvider.getIfAvailable();
        return (m != null) ? m.moderate(text) : text;
    }

    @PostMapping(value = "/topics", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TopicEntity createTopicViaModerator(@RequestBody CreateTopicDto dto) {
        return forum.createTopic(dto.title(), dto.author());
    }

    @PostMapping(value = "/topics/{topicId}/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PostEntity addPostViaModerator(@PathVariable Long topicId, @RequestBody CreatePostDto dto) {
                if (!forum.topicExists(topicId)) {
                        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
                    }
               return forum.addPost(topicId, dto.author(), dto.text());
            }


    @PostMapping("/users/{username}/ban")
    @Operation(summary = "Заблокувати користувача", 
               description = "👮 MODERATOR - блокування користувача",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> banUser(@PathVariable String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Користувача не знайдено"));
        
        if (user.getRole() == UserEntity.Role.MODERATOR || user.getRole() == UserEntity.Role.EDITOR) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Не можна заблокувати модератора або редактора"));
        }
        
        user.setEnabled(false);
        userRepository.save(user);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Користувача заблоковано");
        response.put("username", username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/{username}/unban")
    @Operation(summary = "Розблокувати користувача",
               description = "👮 MODERATOR - розблокування користувача",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<?> unbanUser(@PathVariable String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Користувача не знайдено"));

        user.setEnabled(true);
        userRepository.save(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "Користувача розблоковано");
        response.put("username", username);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/teams/refresh")
    @Operation(summary = "Оновити команди з API",
               description = "👮 MODERATOR - примусове оновлення команд з зовнішнього API в базу даних",
               security = @SecurityRequirement(name = "bearerAuth"))
    public ResponseEntity<Map<String, String>> refreshTeamsFromApi() {
        try {
            log.info("👮 MODERATOR: Запит на примусове оновлення команд з API");

            Map<String, List<Team>> teams = externalTeamApiService.getTeamsFromApi();
            Map<String, String> result = new HashMap<>();
            result.put("status", "success");
            result.put("message", "Команди успішно оновлено з API. Загальна кількість: " +
                    teams.values().stream().mapToInt(List::size).sum() + " команд з " + teams.size() + " ліг");

            if ("success".equals(result.get("status"))) {
                log.info("✅ MODERATOR: Команди успішно оновлено з API");
                return ResponseEntity.ok(result);
            } else if ("warning".equals(result.get("status"))) {
                log.warn("⚠️ MODERATOR: Попередження при оновленні команд: {}", result.get("message"));
                return ResponseEntity.ok(result);
            } else {
                log.error("❌ MODERATOR: Помилка при оновленні команд: {}", result.get("message"));
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }

        } catch (Exception e) {
            log.error("❌ MODERATOR: Критична помилка при оновленні команд: {}", e.getMessage());

            Map<String, String> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", "Критична помилка: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResult);
        }
    }


    private Path getPathToResources() throws IOException {
        Path projectRoot = Paths.get(new File(".").getAbsolutePath()).getParent();
        return projectRoot.resolve("src").resolve("main").resolve("resources");
    }
    public record CreateTopicDto(String title, String author) {}
    public record CreatePostDto(String author, String text) {}

}