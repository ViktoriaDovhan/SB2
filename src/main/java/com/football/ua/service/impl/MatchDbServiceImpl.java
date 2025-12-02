package com.football.ua.service.impl;

import com.football.ua.model.entity.MatchEntity;
import com.football.ua.model.entity.TeamEntity;
import com.football.ua.repo.MatchRepository;
import com.football.ua.repo.TeamRepository;
import com.football.ua.service.MatchDbService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MatchDbServiceImpl implements MatchDbService {

    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    public MatchDbServiceImpl(MatchRepository matchRepository, TeamRepository teamRepository) {
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
    }

    @Override
    @CacheEvict(value = "matches", allEntries = true)
    public MatchEntity create(String homeTeam, String awayTeam, LocalDateTime kickoffAt, String league) {

        TeamEntity home = teamRepository.findByNameAndLeague(homeTeam, league)
                .orElseGet(() -> {
                    TeamEntity t = new TeamEntity();
                    t.setName(homeTeam);
                    t.setLeague(league); // якщо є таке поле
                    return teamRepository.save(t);
                });

        TeamEntity away = teamRepository.findByNameAndLeague(awayTeam, league)
                .orElseGet(() -> {
                    TeamEntity t = new TeamEntity();
                    t.setName(awayTeam);
                    t.setLeague(league); // якщо є
                    return teamRepository.save(t);
                });

        MatchEntity match = new MatchEntity();
        match.setKickoffAt(kickoffAt);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        match.setLeague(league);

        List<MatchEntity> existing = matchRepository.findByHomeTeamAndAwayTeamAndKickoffAt(home, away, kickoffAt);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        return matchRepository.save(match);
    }


    @Override
    @CacheEvict(value = "matches", allEntries = true)
    public MatchEntity updateScore(Long id, Integer homeScore, Integer awayScore) {
        MatchEntity m = matchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));
        if (homeScore != null) m.setHomeScore(homeScore);
        if (awayScore != null) m.setAwayScore(awayScore);
        return matchRepository.save(m);
    }

    @Override
    @CacheEvict(value = "matches", allEntries = true)
    public void delete(Long id) {
        matchRepository.deleteById(id);
    }

    @Override
    @Cacheable(value = "matches")
    public List<MatchEntity> list() {
        return matchRepository.findAll();
    }
}


