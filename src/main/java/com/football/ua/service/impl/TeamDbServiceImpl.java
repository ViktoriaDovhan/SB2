package com.football.ua.service.impl;

import com.football.ua.model.entity.TeamEntity;
import com.football.ua.repo.TeamRepository;
import com.football.ua.service.TeamDbService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TeamDbServiceImpl implements TeamDbService {

    private final TeamRepository teamRepository;

    public TeamDbServiceImpl(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    public TeamEntity create(String name) {
        return teamRepository.findByName(name).orElseGet(() -> {
            TeamEntity t = new TeamEntity();
            t.setName(name);
            return teamRepository.save(t);
        });
    }

    @Override
    public List<TeamEntity> findAll() {
        return teamRepository.findAll();
    }

    @Override
    public Optional<TeamEntity> findByName(String name) {
        return teamRepository.findByName(name);
    }
}


