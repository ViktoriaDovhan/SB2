package com.football.ua.service.impl;

import com.football.ua.model.entity.PostEntity;
import com.football.ua.model.entity.TopicEntity;
import com.football.ua.repo.PostRepository;
import com.football.ua.repo.TopicRepository;
import com.football.ua.service.ForumDbService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import com.football.ua.service.ModerationService;
import org.springframework.beans.factory.ObjectProvider;
import com.football.ua.exception.BadRequestException;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ForumDbServiceImpl implements ForumDbService {

    private final TopicRepository topicRepository;
    private final PostRepository postRepository;
    private final ObjectProvider<ModerationService> moderationProvider;


    public ForumDbServiceImpl(TopicRepository topicRepository, PostRepository postRepository, ObjectProvider<ModerationService> moderationProvider) {
        this.topicRepository = topicRepository;
        this.postRepository = postRepository;
        this.moderationProvider = moderationProvider;
    }

    private String validateNoProfanity(String fieldName, String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        ModerationService moderation = moderationProvider.getIfAvailable();
        if (moderation != null && moderation.containsProfanity(trimmed)) {
            throw new BadRequestException("Поле \"" + fieldName + "\" містить заборонені слова. Будь ласка, приберіть лайку.");
        }
        return trimmed;
}

    @Override
    @Transactional
    public TopicEntity createTopic(String title, String author) {
        TopicEntity t = new TopicEntity();
        t.setTitle(validateNoProfanity("title", title));
        t.setAuthor(validateNoProfanity("author", author));
        return topicRepository.save(t);
    }

    @Override
    @Transactional
    public void deleteTopic(Long id) {
        topicRepository.deleteById(id);
    }

    @Override
    public List<TopicEntity> listTopics() {
        return topicRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
    }
    @Override
    @Transactional
    public PostEntity addPost(Long topicId, String author, String text) {
        TopicEntity topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found"));
        PostEntity p = new PostEntity();
        p.setTopic(topic);
        p.setAuthor(validateNoProfanity("author", author));
        p.setText(validateNoProfanity("text", text));
        return postRepository.save(p);
    }

    @Override
    public List<PostEntity> listPosts(Long topicId) {
        return postRepository.findByTopic_Id(topicId);
    }
    @Override
    public boolean topicExists(Long id) {
        return topicRepository.existsById(id);
    }
}


