package com.football.ua.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FootballApiTeamResponse {
    private List<TeamData> teams;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamData {
        private Long id;
        private String name;
        private String shortName;
        private String tla;
        private Crest crest;
        private String address;
        private String venue;
        
        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Crest {
            private String url;
            
            public String getUrl() {
                return url != null ? url : "";
            }
        }
    }
}

