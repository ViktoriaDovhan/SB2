package com.football.ua.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiFootballTeamsResponse {
    private List<Response> response;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {
        private TeamInfo team;
        private VenueInfo venue;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private Long id;
        private String name;
        private String code;
        private String country;
        private Integer founded;
        private String logo;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VenueInfo {
        private String name;
        private String city;
    }
}

