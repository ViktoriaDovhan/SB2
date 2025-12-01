package com.football.ua.repo;

import com.football.ua.model.entity.MatchEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchRepository extends JpaRepository<MatchEntity, Long> {
    List<MatchEntity> findByLeague(String league);
    List<MatchEntity> findByLeagueAndKickoffAtAfter(String league, LocalDateTime date);
    List<MatchEntity> findByLeagueAndKickoffAtBefore(String league, LocalDateTime date);
    void deleteByLeague(String league);
    List<MatchEntity> findByHomeTeamAndAwayTeamAndKickoffAt(com.football.ua.model.entity.TeamEntity homeTeam, com.football.ua.model.entity.TeamEntity awayTeam, LocalDateTime kickoffAt);
}


