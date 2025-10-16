package com.football.ua.service;

import java.time.LocalDateTime;

public interface MatchService {
    String schedule(String homeTeam, String awayTeam, LocalDateTime when);
    double predictHomeWin(String homeTeam, String awayTeam);
}