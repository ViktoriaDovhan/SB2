package com.football.ua.service;

import com.football.ua.model.entity.TeamEntity;

import java.util.List;
import java.util.Optional;

public interface TeamDbService {
    TeamEntity create(String name);
    List<TeamEntity> findAll();
    Optional<TeamEntity> findByName(String name);
}


