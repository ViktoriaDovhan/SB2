package com.football.ua.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.football.ua.model.entity.PostEntity;
import com.football.ua.model.entity.TopicEntity;
import com.football.ua.service.ForumDbService;
import com.football.ua.service.ModerationService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.ObjectProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/moderator")
public class ModeratorController {

    private final ObjectMapper objectMapper;
    private final Path resourcesPath;
    private final ForumDbService forum;
    private final ObjectProvider<ModerationService> moderationProvider;

    public ModeratorController(ObjectMapper objectMapper, ForumDbService forum, ObjectProvider<ModerationService> moderationProvider) throws IOException {
        this.objectMapper = objectMapper;
        this.forum = forum;
        this.moderationProvider = moderationProvider;
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
    @ResponseStatus(HttpStatus.CREATED) // зберігаємо твою семантику CREATED
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


    private Path getPathToResources() throws IOException {
        Path projectRoot = Paths.get(new File(".").getAbsolutePath()).getParent();
        return projectRoot.resolve("src").resolve("main").resolve("resources");
    }
    public record CreateTopicDto(String title, String author) {}
    public record CreatePostDto(String author, String text) {}

}