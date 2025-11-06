package com.football.ua.service.impl;

import com.football.ua.model.entity.MatchEntity;
import com.football.ua.model.entity.TeamEntity;
import com.football.ua.repo.MatchRepository;
import com.football.ua.repo.TeamRepository;
import com.football.ua.service.MatchDbService;
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
    public MatchEntity create(String homeTeam, String awayTeam, LocalDateTime kickoffAt) {
        TeamEntity home = teamRepository.findByName(homeTeam).orElseGet(() -> {
            TeamEntity t = new TeamEntity();
            t.setName(homeTeam);
            return teamRepository.save(t);
        });
        TeamEntity away = teamRepository.findByName(awayTeam).orElseGet(() -> {
            TeamEntity t = new TeamEntity();
            t.setName(awayTeam);
            return teamRepository.save(t);
        });

        MatchEntity match = new MatchEntity();
        match.setKickoffAt(kickoffAt);
        match.setHomeScore(0);
        match.setAwayScore(0);
        match.setHomeTeam(home);
        match.setAwayTeam(away);
        return matchRepository.save(match);
    }

    @Override
    public MatchEntity updateScore(Long id, Integer homeScore, Integer awayScore) {
        MatchEntity m = matchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Match not found"));
        if (homeScore != null) m.setHomeScore(homeScore);
        if (awayScore != null) m.setAwayScore(awayScore);
        return matchRepository.save(m);
    }

    @Override
    public void delete(Long id) {
        matchRepository.deleteById(id);
    }

    @Override
    public List<MatchEntity> list() {
        return matchRepository.findAll();
    }
}


