package com.football.ua.service;

import java.time.LocalDateTime;
import java.util.List;


public interface UpcomingMatchNotificationService {

    List<UpcomingMatchNotification> getUpcomingMatches();

    record UpcomingMatchNotification(
        Long matchId,
        String homeTeam,
        String awayTeam,
        LocalDateTime kickoffAt,
        long hoursUntilMatch
    ) {}
}


