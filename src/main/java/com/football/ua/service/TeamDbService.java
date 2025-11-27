package com.football.ua.service;

import com.football.ua.model.Team;
import com.football.ua.model.entity.TeamEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TeamDbService {

    TeamEntity create(String name);

    TeamEntity saveOrUpdateTeam(Team team);
    void saveOrUpdateTeams(List<Team> teams);
    Map<String, List<Team>> getAllTeams();
    List<Team> getTeamsByLeague(String league);

    int deactivateOldTeams(String league, LocalDateTime cutoffDate);
    List<TeamEntity> getTeamsNeedingUpdate(LocalDateTime cutoffDate);

    List<TeamEntity> findAll();
    Optional<TeamEntity> findByName(String name);
    List<TeamEntity> findByLeague(String league);
    boolean existsByNameAndLeague(String name, String league);
}



