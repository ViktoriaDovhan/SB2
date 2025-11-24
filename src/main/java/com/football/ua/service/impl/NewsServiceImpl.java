package com.football.ua.service.impl;

import com.football.ua.model.News;
import com.football.ua.repo.NewsRepository;
import com.football.ua.service.ModerationService;
import com.football.ua.service.NewsService;
import com.football.ua.service.NotificationService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;


@Service
public class NewsServiceImpl implements NewsService {

    private final ModerationService moderationService;
    private final NotificationService notificationService;
    private final NewsRepository newsRepository;


    public NewsServiceImpl(ModerationService moderationService,
                           NotificationService notificationService, NewsRepository newsRepository) {
        this.moderationService = moderationService;
        this.notificationService = notificationService;
        this.newsRepository = newsRepository;
    }

    @Override
    public String publish(String title, String body) {
        String cleanBody = moderationService.moderate(body);
        notificationService.notifyAll("news", "Published: " + title);
        return cleanBody;
    }

    @Cacheable(value = "news", key = "#newsId")
    public News getNewsById(long newsId) {
        News news = newsRepository.findById(newsId).orElseThrow();
        return news;
    }
}
