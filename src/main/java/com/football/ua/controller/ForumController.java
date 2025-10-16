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

    public ForumController(ForumDbService forum) {
        this.forum = forum;
    }

    @GetMapping("/topics")
    public List<TopicEntity> listTopics() {
        return forum.listTopics();
    }

    @PostMapping(value = "/topics", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TopicEntity createTopic(@RequestBody TopicCreateDto dto) {
        return forum.createTopic(dto.title(), dto.author());
    }

    @DeleteMapping("/topics/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTopic(@PathVariable Long id) {
        if (!forum.topicExists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Topic not found");
        }
        forum.deleteTopic(id);
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
        return forum.addPost(topicId, dto.author(), dto.text());
    }

    public record TopicCreateDto(String title, String author) {}
    public record PostCreateDto(String author, String text) {}
}
