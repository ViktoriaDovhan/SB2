package com.football.ua.service.impl;

import com.football.ua.service.ModerationService;
import com.football.ua.util.ContentFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "football.comments.automoderation.enabled", havingValue = "true")
@ConditionalOnBean(ContentFilter.class)
public class ModerationServiceImpl implements ModerationService {

    private final ContentFilter contentFilter;

    public ModerationServiceImpl(ContentFilter contentFilter) {
        this.contentFilter = contentFilter;
    }

    @Override
    public boolean containsProfanity(String text) {
        if (text == null || text.isEmpty()) return false;
        return !text.equals(contentFilter.filter(text));
    }

    @Override
    public String moderate(String text) {
        if (text == null) return null;
        return contentFilter.filter(text.trim());
    }
}
