package com.football.ua.service.impl;

import com.football.ua.model.entity.MatchEntity;
import com.football.ua.repo.MatchRepository;
import com.football.ua.service.NotificationService;
import com.football.ua.service.UpcomingMatchNotificationService;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class UpcomingMatchNotificationServiceImpl implements UpcomingMatchNotificationService {
    
    private final MatchRepository matchRepository;
    private final NotificationService notificationService;
    
    public UpcomingMatchNotificationServiceImpl(
            MatchRepository matchRepository,
            NotificationService notificationService) {
        this.matchRepository = matchRepository;
        this.notificationService = notificationService;
    }
    
    @PostConstruct
    public void init() {
        checkAndNotifyUpcomingMatches();
    }
    

    @Scheduled(fixedRate = 1800000)
    public void checkAndNotifyUpcomingMatches() {
        List<UpcomingMatchNotification> upcomingMatches = getUpcomingMatches();
        
        if (!upcomingMatches.isEmpty()) {
            notificationService.notifyAll(
                "UPCOMING_MATCHES", 
                String.format("Знайдено %d матчі(ів) на найближчі 2 дні", upcomingMatches.size())
            );
        }
    }
    
    @Override
    public List<UpcomingMatchNotification> getUpcomingMatches() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoDaysLater = now.plusHours(48);

        List<MatchEntity> allMatches = matchRepository.findAll();

        List<UpcomingMatchNotification> result = allMatches.stream()
            .filter(match -> {
                LocalDateTime kickoff = match.getKickoffAt();
                boolean isAfterNow = kickoff.isAfter(now);
                boolean isBeforeTwoDays = kickoff.isBefore(twoDaysLater);
                boolean include = isAfterNow && isBeforeTwoDays;

                return include;
            })
            .map(this::toNotification)
            .collect(Collectors.toList());

        return result;
    }
    
    private UpcomingMatchNotification toNotification(MatchEntity match) {
        LocalDateTime now = LocalDateTime.now();
        long hoursUntil = Duration.between(now, match.getKickoffAt()).toHours();
        
        String homeTeam = "Команда 1";
        String awayTeam = "Команда 2";
        
        try {
            if (match.getHomeTeam() != null) {
                homeTeam = match.getHomeTeam().getName();
            }
            if (match.getAwayTeam() != null) {
                awayTeam = match.getAwayTeam().getName();
            }
        } catch (Exception e) {
            // Silently handle team name retrieval errors
        }
        
        return new UpcomingMatchNotification(
            match.getId(),
            homeTeam,
            awayTeam,
            match.getKickoffAt(),
            hoursUntil
        );
    }
}

