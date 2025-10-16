package com.football.ua.service.impl;

import com.football.ua.service.StatsCalculator;
import org.springframework.stereotype.Component;

@Component
public class SimpleStatsCalculator implements StatsCalculator {
    @Override
    public double homeWinProbability(String homeTeam, String awayTeam) {
        return 0.55;
    }
}
