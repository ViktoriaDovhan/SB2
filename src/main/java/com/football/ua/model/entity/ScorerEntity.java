package com.football.ua.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "scorers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"player_id", "league"})
})
public class ScorerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "player_id", nullable = false)
    private Integer playerId;

    @Column(name = "player_name", nullable = false)
    private String playerName;

    @Column(name = "team_id")
    private Integer teamId;

    @Column(name = "team_name")
    private String teamName;

    @Column(nullable = false)
    private Integer goals;

    @Column
    private Integer assists;

    @Column
    private Integer penalties;

    @Column(nullable = false)
    private String league;

    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;

    public ScorerEntity() {
        this.lastUpdated = LocalDateTime.now();
    }

    public ScorerEntity(Integer playerId, String playerName, Integer teamId, String teamName, 
                        Integer goals, Integer assists, Integer penalties, String league) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.teamId = teamId;
        this.teamName = teamName;
        this.goals = goals;
        this.assists = assists;
        this.penalties = penalties;
        this.league = league;
        this.lastUpdated = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getPlayerId() { return playerId; }
    public void setPlayerId(Integer playerId) { this.playerId = playerId; }

    public String getPlayerName() { return playerName; }
    public void setPlayerName(String playerName) { this.playerName = playerName; }

    public Integer getTeamId() { return teamId; }
    public void setTeamId(Integer teamId) { this.teamId = teamId; }

    public String getTeamName() { return teamName; }
    public void setTeamName(String teamName) { this.teamName = teamName; }

    public Integer getGoals() { return goals; }
    public void setGoals(Integer goals) { this.goals = goals; }

    public Integer getAssists() { return assists; }
    public void setAssists(Integer assists) { this.assists = assists; }

    public Integer getPenalties() { return penalties; }
    public void setPenalties(Integer penalties) { this.penalties = penalties; }

    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}
