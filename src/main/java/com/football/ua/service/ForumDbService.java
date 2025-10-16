package com.football.ua.service;

import com.football.ua.model.entity.PostEntity;
import com.football.ua.model.entity.TopicEntity;

import java.util.List;

public interface ForumDbService {
    boolean topicExists(Long id);
    TopicEntity createTopic(String title, String author);
    void deleteTopic(Long id);
    List<TopicEntity> listTopics();
    PostEntity addPost(Long topicId, String author, String text);
    List<PostEntity> listPosts(Long topicId);
}


