package com.football.ua.config;

import com.football.ua.util.ContentFilter;
import com.football.ua.util.ProfanityContentFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.Set;
import java.util.List;

@Configuration
@EnableAspectJAutoProxy
@EnableCaching
@EnableConfigurationProperties(AppConfig.ModerationProps.class)
public class AppConfig {

    @Bean
    @ConditionalOnProperty(name = "football.comments.automoderation.enabled", havingValue = "true")
    public ContentFilter contentFilter(ModerationProps props) {
        System.out.println("BANNED = " + props.banned());
        return new ProfanityContentFilter(Set.copyOf(props.banned()));
    }

    @Bean
    public Clock systemClock() {
        return Clock.systemDefaultZone();
    }

    
    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new CustomCacheManager();
    }

    @ConfigurationProperties(prefix = "football.comments")
    public record ModerationProps(Automoderation automoderation, List<String> banned) {
        public record Automoderation(boolean enabled) {}
    }
}


