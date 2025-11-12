package com.football.ua.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.ZonedDateTime;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FootballDataResponse {
    private List<TeamData> teams;
    private List<MatchData> matches;

    public List<TeamData> getTeams() {
        return teams;
    }

    public void setTeams(List<TeamData> teams) {
        this.teams = teams;
    }

    public List<MatchData> getMatches() {
        return matches;
    }

    public void setMatches(List<MatchData> matches) {
        this.matches = matches;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamData {
        private Long id;
        private String name;
        private String shortName;
        private String tla;
        private String crest;
        private String address;
        private String venue;
        private String founded;
        private String clubColors;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        public String getTla() {
            return tla;
        }

        public void setTla(String tla) {
            this.tla = tla;
        }

        public String getCrest() {
            return crest;
        }

        public void setCrest(String crest) {
            this.crest = crest;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getVenue() {
            return venue;
        }

        public void setVenue(String venue) {
            this.venue = venue;
        }

        public String getFounded() {
            return founded;
        }

        public void setFounded(String founded) {
            this.founded = founded;
        }

        public String getClubColors() {
            return clubColors;
        }

        public void setClubColors(String clubColors) {
            this.clubColors = clubColors;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchData {
        private Long id;
        private String utcDate;
        private String status;
        private Integer matchday;
        private String stage;
        private MatchTeam homeTeam;
        private MatchTeam awayTeam;
        private MatchScore score;
        private Competition competition;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUtcDate() {
            return utcDate;
        }

        public void setUtcDate(String utcDate) {
            this.utcDate = utcDate;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Integer getMatchday() {
            return matchday;
        }

        public void setMatchday(Integer matchday) {
            this.matchday = matchday;
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public MatchTeam getHomeTeam() {
            return homeTeam;
        }

        public void setHomeTeam(MatchTeam homeTeam) {
            this.homeTeam = homeTeam;
        }

        public MatchTeam getAwayTeam() {
            return awayTeam;
        }

        public void setAwayTeam(MatchTeam awayTeam) {
            this.awayTeam = awayTeam;
        }

        public MatchScore getScore() {
            return score;
        }

        public void setScore(MatchScore score) {
            this.score = score;
        }

        public Competition getCompetition() {
            return competition;
        }

        public void setCompetition(Competition competition) {
            this.competition = competition;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchTeam {
        private Long id;
        private String name;
        private String shortName;
        private String tla;
        private String crest;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getShortName() {
            return shortName;
        }

        public void setShortName(String shortName) {
            this.shortName = shortName;
        }

        public String getTla() {
            return tla;
        }

        public void setTla(String tla) {
            this.tla = tla;
        }

        public String getCrest() {
            return crest;
        }

        public void setCrest(String crest) {
            this.crest = crest;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MatchScore {
        private String winner;
        private String duration;
        private ScoreDetail fullTime;
        private ScoreDetail halfTime;

        public String getWinner() {
            return winner;
        }

        public void setWinner(String winner) {
            this.winner = winner;
        }

        public String getDuration() {
            return duration;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }

        public ScoreDetail getFullTime() {
            return fullTime;
        }

        public void setFullTime(ScoreDetail fullTime) {
            this.fullTime = fullTime;
        }

        public ScoreDetail getHalfTime() {
            return halfTime;
        }

        public void setHalfTime(ScoreDetail halfTime) {
            this.halfTime = halfTime;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreDetail {
        private Integer home;
        private Integer away;

        public Integer getHome() {
            return home;
        }

        public void setHome(Integer home) {
            this.home = home;
        }

        public Integer getAway() {
            return away;
        }

        public void setAway(Integer away) {
            this.away = away;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Competition {
        private Long id;
        private String name;
        private String code;
        private String emblem;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getEmblem() {
            return emblem;
        }

        public void setEmblem(String emblem) {
            this.emblem = emblem;
        }
    }
}

