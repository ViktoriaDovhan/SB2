package com.football.ua.service;

import com.football.ua.model.entity.MatchEntity;

import java.time.LocalDateTime;
import java.util.List;

public interface MatchDbService {
    MatchEntity create(String homeTeam, String awayTeam, LocalDateTime kickoffAt);
    MatchEntity updateScore(Long id, Integer homeScore, Integer awayScore);
    void delete(Long id);
    List<MatchEntity> list();
}


