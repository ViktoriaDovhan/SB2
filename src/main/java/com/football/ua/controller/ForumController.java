package com.football.ua.controller;

import com.football.ua.model.entity.PostEntity;
import com.football.ua.model.entity.TopicEntity;
import com.football.ua.service.AuthorizationService;
import com.football.ua.service.ForumDbService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/forum")
@Tag(name = "💬 Forum", description = "API для форуму (PUBLIC для читання, AUTHENTICATED для створення)")
public class ForumController {

    private final ForumDbService forum;
    private final com.football.ua.service.ActivityLogService activityLogService;
    private final AuthorizationService authorizationService;

    public ForumController(ForumDbService forum, 
                          com.football.ua.service.ActivityLogService activityLogService,
                          AuthorizationService authorizationService) {
        this.forum = forum;
        this.activityLogService = activityLogService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/topics")
    @Operation(summary = "Отримати всі теми форуму", 
               description = "🌐 PUBLIC - доступно без автентифікації")
    public List<TopicEntity> listTopics() {
        return forum.listTopics();
    }

    @PostMapping(value = "/topics", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('USER', 'MODERATOR', 'EDITOR')")
    @Operation(summary = "Створити нову тему",
               description = "Потрібна роль: USER, MODERATOR або EDITOR",
               security = @SecurityRequirement(name = "bearerAuth"))
    public TopicEntity createTopic(@RequestBody TopicCreateDto dto, Authentication auth) {
        String author = (auth != null && auth.isAuthenticated()) ? auth.getName() : dto.author();
        TopicEntity topic = forum.createTopic(dto.title(), author);
        
        activityLogService.logActivity(
            "Створено нову тему на форумі",
            String.format("\"%s\" від %s", dto.title(), author),
            "FORUM"
        );
        
        return topic;
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Видалити тему",
               description = "Власник теми або MODERATOR",
               security = @SecurityRequirement(name = "bearerAuth"))
    public void deleteTopic(@PathVariable Long id, Authentication authentication) {
        if (!forum.topicExists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }

        if (!authorizationService.canDeleteTopic(id, authentication)) {
            throw new AccessDeniedException("Ви не маєте прав видаляти цю тему");
        }
        
        forum.deleteTopic(id);
        
        String action = authorizationService.isModeratorOrHigher(authentication) 
            ? "видалена модератором" 
            : "видалена автором";
        
        activityLogService.logActivity(
            "Видалено тему з форуму",
            String.format("Тема #%d %s", id, action),
            "FORUM"
        );
    }

    @GetMapping("/topics/{topicId}/posts")
    public List<PostEntity> listPosts(@PathVariable Long topicId) {
        if (!forum.topicExists(topicId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        return forum.listPosts(topicId);
    }


    @PostMapping(value = "/topics/{topicId}/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('USER', 'MODERATOR', 'EDITOR')")
    public PostEntity addPost(@PathVariable Long topicId, @RequestBody PostCreateDto dto, Authentication auth) {
        if (!forum.topicExists(topicId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        String author = (auth != null && auth.isAuthenticated()) ? auth.getName() : dto.author();
        PostEntity post = forum.addPost(topicId, author, dto.text());
        
        activityLogService.logActivity(
            "Додано коментар на форумі",
            String.format("%s залишив коментар у темі #%d", author, topicId),
            "FORUM"
        );
        
        return post;
    }

    @DeleteMapping("/topics/{topicId}/posts/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Видалити пост", 
               description = "🔐 AUTHENTICATED - власник поста або 👮 MODERATOR",
               security = @SecurityRequirement(name = "bearerAuth"))
    public void deletePost(@PathVariable Long topicId, @PathVariable Long postId, Authentication authentication) {
        if (!forum.topicExists(topicId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }

        if (!authorizationService.canDeletePost(postId, authentication)) {
            throw new AccessDeniedException("Ви не маєте прав видаляти цей пост");
        }
        
        forum.deletePost(postId);
        
        String action = authorizationService.isModeratorOrHigher(authentication) 
            ? "видалено модератором" 
            : "видалено автором";
        
        activityLogService.logActivity(
            "Видалено коментар з форуму",
            String.format("Пост #%d з теми #%d %s", postId, topicId, action),
            "FORUM"
        );
    }

    public record TopicCreateDto(String title, String author) {}
    public record PostCreateDto(String author, String text) {}
}
