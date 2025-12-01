package com.football.ua.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "standings", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"team_name", "league"})
})
public class StandingEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "team_name", nullable = false)
    private String teamName;

    @Column(name = "team_crest")
    private String teamCrest;

    @Column(name = "played_games", nullable = false)
    private Integer playedGames;

    @Column(nullable = false)
    private Integer won;

    @Column(nullable = false)
    private Integer draw;

    @Column(nullable = false)
    private Integer lost;

    @Column(name = "goals_for", nullable = false)
    private Integer goalsFor;

    @Column(name = "goals_against", nullable = false)
    private Integer goalsAgainst;

    @Column(name = "goal_difference", nullable = false)
    private Integer goalDifference;

    @Column(nullable = false)
    private Integer points;

    @Column(nullable = false)
    private String league;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    public StandingEntity() {
        this.lastUpdated = LocalDateTime.now();
    }

    public StandingEntity(Integer position, String teamName, String teamCrest, 
                          Integer playedGames, Integer won, Integer draw, Integer lost,
                          Integer goalsFor, Integer goalsAgainst, Integer goalDifference, 
                          Integer points, String league) {
        this.position = position;
        this.teamName = teamName;
        this.teamCrest = teamCrest;
        this.playedGames = playedGames;
        this.won = won;
        this.draw = draw;
        this.lost = lost;
        this.goalsFor = goalsFor;
        this.goalsAgainst = goalsAgainst;
        this.goalDifference = goalDifference;
        this.points = points;
        this.league = league;
        this.lastUpdated = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public String getTeamCrest() { return teamCrest; }
    public void setTeamCrest(String teamCrest) { this.teamCrest = teamCrest; }

    public Integer getPlayedGames() { return playedGames; }
    public void setPlayedGames(Integer playedGames) { this.playedGames = playedGames; }

    public Integer getWon() { return won; }
    public void setWon(Integer won) { this.won = won; }

    public Integer getDraw() { return draw; }
    public void setDraw(Integer draw) { this.draw = draw; }

    public Integer getLost() { return lost; }
    public void setLost(Integer lost) { this.lost = lost; }

    public Integer getGoalsFor() { return goalsFor; }
    public void setGoalsFor(Integer goalsFor) { this.goalsFor = goalsFor; }

    public Integer getGoalsAgainst() { return goalsAgainst; }
    public void setGoalsAgainst(Integer goalsAgainst) { this.goalsAgainst = goalsAgainst; }

    public Integer getGoalDifference() { return goalDifference; }
    public void setGoalDifference(Integer goalDifference) { this.goalDifference = goalDifference; }

    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }

    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
