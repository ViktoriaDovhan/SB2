package com.football.ua.service.impl;

import com.football.ua.service.MatchService;
import com.football.ua.service.StatsCalculator;
import com.football.ua.service.TeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MatchServiceImpl implements MatchService {

    private final TeamService teamService;
    private StatsCalculator statsCalculator;

    @Value("${football.prediction.home-advantage-enabled:false}")
    private boolean isHomeAdvantageEnabled;
    private static final double HOME_ADVANTAGE_BONUS = 0.15;

    public MatchServiceImpl(TeamService teamService) {
        this.teamService = teamService;
    }

    @Autowired
    public void setStatsCalculator(StatsCalculator statsCalculator) {
        this.statsCalculator = statsCalculator;
    }

    @Override
    public String schedule(String homeTeam, String awayTeam, LocalDateTime when) {
        if (!teamService.exists(homeTeam) || !teamService.exists(awayTeam)) {
            throw new IllegalArgumentException("Unknown team(s)");
        }
        return "Match " + homeTeam + " vs " + awayTeam + " at " + when;
    }

    @Override
    public double predictHomeWin(String homeTeam, String awayTeam) {
        double baseProbability = (statsCalculator == null) ? 0.5
                : statsCalculator.homeWinProbability(homeTeam, awayTeam);

        if (isHomeAdvantageEnabled) {
            return Math.min(baseProbability + HOME_ADVANTAGE_BONUS, 0.99);
        } else {
            return baseProbability;
        }
    }
}
