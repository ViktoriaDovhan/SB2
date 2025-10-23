package com.football.ua.controller;

import com.football.ua.model.entity.PostEntity;
import com.football.ua.model.entity.TopicEntity;
import com.football.ua.service.ForumDbService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/forum")
public class ForumController {

    private final ForumDbService forum;
    private final com.football.ua.service.ActivityLogService activityLogService;

    public ForumController(ForumDbService forum, com.football.ua.service.ActivityLogService activityLogService) {
        this.forum = forum;
        this.activityLogService = activityLogService;
    }

    @GetMapping("/topics")
    public List<TopicEntity> listTopics() {
        return forum.listTopics();
    }

    @PostMapping(value = "/topics", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TopicEntity createTopic(@RequestBody TopicCreateDto dto) {
        TopicEntity topic = forum.createTopic(dto.title(), dto.author());
        
        activityLogService.logActivity(
            "Створено нову тему на форумі",
            String.format("\"%s\" від %s", dto.title(), dto.author()),
            "FORUM"
        );
        
        return topic;
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable Long id) {
        if (!forum.topicExists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        forum.deleteTopic(id);
        
        activityLogService.logActivity(
            "Видалено тему з форуму",
            String.format("Тема #%d видалена модератором", id),
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
    public PostEntity addPost(@PathVariable Long topicId, @RequestBody PostCreateDto dto) {
        if (!forum.topicExists(topicId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        PostEntity post = forum.addPost(topicId, dto.author(), dto.text());
        
        activityLogService.logActivity(
            "Додано коментар на форумі",
            String.format("%s залишив коментар у темі #%d", dto.author(), topicId),
            "FORUM"
        );
        
        return post;
    }

    public record TopicCreateDto(String title, String author) {}
    public record PostCreateDto(String author, String text) {}
}
