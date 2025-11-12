package com.football.ua.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${football.api.key:}")
    private String apiKey;

    @Value("${football.api.base-url:https://api.football-data.org/v4}")
    private String apiBaseUrl;

    @Bean
    public WebClient footballApiWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader("X-Auth-Token", apiKey)
                .build();
    }
}

