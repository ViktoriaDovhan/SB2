package com.football.ua.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Service
@ConditionalOnResource(resources = "classpath:player-of-the-week.json")
public class PlayerOfTheWeekServiceImpl {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    @Getter
    private Map<String, Object> playerData;

    public PlayerOfTheWeekServiceImpl(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    private void loadData() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:player-of-the-week.json");
        try (InputStream inputStream = resource.getInputStream()) {
            this.playerData = objectMapper.readValue(inputStream, new TypeReference<>() {});
        }
    }

}