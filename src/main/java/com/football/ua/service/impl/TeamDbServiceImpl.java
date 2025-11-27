package com.football.ua.service.impl;

import com.football.ua.model.Team;
import com.football.ua.model.entity.TeamEntity;
import com.football.ua.repo.TeamRepository;
import com.football.ua.service.TeamDbService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class TeamDbServiceImpl implements TeamDbService {
    private static final Logger log = LoggerFactory.getLogger(TeamDbServiceImpl.class);

    private final TeamRepository teamRepository;

    public TeamDbServiceImpl(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    @CacheEvict(value = "teams", allEntries = true)
    public TeamEntity create(String name) {
        return teamRepository.findByName(name).orElseGet(() -> {
            TeamEntity t = new TeamEntity();
            t.setName(name);

            t.setLeague("UNKNOWN");
            t.setLastUpdated(LocalDateTime.now());
            t.setActive(true);
            return teamRepository.save(t);
        });
    }

    @Override
    @Transactional
    @CacheEvict(value = "teams", allEntries = true)
    public TeamEntity saveOrUpdateTeam(Team team) {
        Optional<TeamEntity> existingTeam = teamRepository.findByNameAndLeague(team.name, team.league);

        if (existingTeam.isPresent()) {

            TeamEntity entity = existingTeam.get();
            entity.setCity(team.city);
            entity.setColors(team.colors);
            entity.setEmblemUrl(team.emblemUrl);
            entity.setLastUpdated(LocalDateTime.now());
            entity.setActive(true);
            return teamRepository.save(entity);
        } else {

            TeamEntity newEntity = new TeamEntity(team.name, team.league, team.city, team.colors, team.emblemUrl);
            return teamRepository.save(newEntity);
        }
    }

    @Override
    @Transactional
    @CacheEvict(value = "teams", allEntries = true)
    public void saveOrUpdateTeams(List<Team> teams) {
        for (Team team : teams) {
            saveOrUpdateTeam(team);
        }
        log.info("Збережено/оновлено {} команд в БД", teams.size());
    }

    @Override
    @Cacheable(value = "teams")
    public Map<String, List<Team>> getAllTeams() {
        List<TeamEntity> entities = teamRepository.findByActiveTrue();

        return entities.stream()
                .collect(Collectors.groupingBy(
                        TeamEntity::getLeague,
                        Collectors.mapping(this::convertToTeam, Collectors.toList())
                ));
    }

    @Override
    @Cacheable(value = "teams", key = "#league")
    public List<Team> getTeamsByLeague(String league) {
        List<TeamEntity> entities = teamRepository.findByLeagueAndActiveTrue(league);
        return entities.stream()
                .map(this::convertToTeam)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    @CacheEvict(value = "teams", allEntries = true)
    public int deactivateOldTeams(String league, LocalDateTime cutoffDate) {
        int deactivated = teamRepository.deactivateOldTeams(league, cutoffDate);
        if (deactivated > 0) {
            log.info("Позначено {} застарілих команд як неактивні для ліги {}", deactivated, league);
        }
        return deactivated;
    }

    @Override
    public List<TeamEntity> getTeamsNeedingUpdate(LocalDateTime cutoffDate) {
        return teamRepository.findTeamsNeedingUpdate(cutoffDate);
    }

    @Override
    @Cacheable(value = "teams")
    public List<TeamEntity> findAll() {
        return teamRepository.findAll();
    }

    @Override
    @Cacheable(value = "teams", key = "#name")
    public Optional<TeamEntity> findByName(String name) {
        return teamRepository.findByName(name);
    }

    @Override
    public List<TeamEntity> findByLeague(String league) {
        return teamRepository.findByLeague(league);
    }

    @Override
    public boolean existsByNameAndLeague(String name, String league) {
        return teamRepository.findByNameAndLeague(name, league).isPresent();
    }

    
    private Team convertToTeam(TeamEntity entity) {
        Team team = new Team();
        team.id = entity.getId();
        team.name = entity.getName();
        team.league = entity.getLeague();
        team.city = entity.getCity();
        team.colors = entity.getColors();
        team.emblemUrl = entity.getEmblemUrl();
        return team;
    }
}




