package com.football.ua.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${football.api.key:}")
    private String apiKey;

    @Value("${football.api.base-url:https://api.football-data.org/v4}")
    private String apiBaseUrl;

    @Bean
    public WebClient footballApiWebClient(WebClient.Builder builder) {
        // Збільшуємо ліміт буфера до 2MB для великих API відповідей
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();

        return builder
                .baseUrl(apiBaseUrl)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .defaultHeader("X-Auth-Token", apiKey)
                .exchangeStrategies(strategies)
                .build();
    }
}

