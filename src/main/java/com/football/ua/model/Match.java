package com.football.ua.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public class Match {
    public Long id;

    @NotBlank public String homeTeam;
    @NotBlank public String awayTeam;

    @Min(0) public Integer homeScore;
    @Min(0) public Integer awayScore;

    @NotNull public LocalDateTime kickoffAt;
}