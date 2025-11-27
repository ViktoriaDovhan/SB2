package com.football.ua.model.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "teams", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"name", "league"})
})
public class TeamEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String league;

    @Column
    private String city;

    @Column
    private String colors;

    @Column
    private String emblemUrl;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private Boolean active = true;

    public TeamEntity() {}

    public TeamEntity(String name, String league, String city, String colors, String emblemUrl) {
        this.name = name;
        this.league = league;
        this.city = city;
        this.colors = colors;
        this.emblemUrl = emblemUrl;
        this.lastUpdated = LocalDateTime.now();
        this.active = true;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLeague() { return league; }
    public void setLeague(String league) { this.league = league; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getColors() { return colors; }
    public void setColors(String colors) { this.colors = colors; }

    public String getEmblemUrl() { return emblemUrl; }
    public void setEmblemUrl(String emblemUrl) { this.emblemUrl = emblemUrl; }

    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
}



