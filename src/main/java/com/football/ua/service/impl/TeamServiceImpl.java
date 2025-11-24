package com.football.ua.service.impl;

import com.football.ua.service.TeamService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class TeamServiceImpl implements TeamService {

    private final Set<String> teams = new HashSet<>();

    @Override
    @Cacheable(value = "teams", key = "#name")
    public boolean exists(String name) {
        return teams.contains(name);
    }

    @Override
    @CacheEvict(value = "teams", key = "#name")
    public void addTeam(String name) {
        teams.add(name);
    }
}
