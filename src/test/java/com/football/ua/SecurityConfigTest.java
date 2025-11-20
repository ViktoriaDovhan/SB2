package com.football.ua;

import com.football.ua.service.MatchDbService;
import com.football.ua.service.ActivityLogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Тести Spring Security")
public class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchDbService matchDbService;

    @MockBean
    private ActivityLogService activityLogService;

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER може переглядати матчі")
    public void testUserCanViewMatches() throws Exception {
        when(matchDbService.list()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/matches"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER не може створювати матчі")
    public void testUserCannotCreateMatches() throws Exception {
        String jsonBody = """
                {
                    "homeTeam": "Динамо",
                    "awayTeam": "Шахтар",
                    "kickoffAt": "2025-11-15T19:00:00",
                    "league": "УПЛ"
                }
                """;

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("EDITOR може створювати матчі")
    public void testEditorCanCreateMatches() throws Exception {
        com.football.ua.model.entity.TeamEntity homeTeam = new com.football.ua.model.entity.TeamEntity();
        homeTeam.setName("Динамо");
        
        com.football.ua.model.entity.TeamEntity awayTeam = new com.football.ua.model.entity.TeamEntity();
        awayTeam.setName("Шахтар");
        
        com.football.ua.model.entity.MatchEntity mockMatch = new com.football.ua.model.entity.MatchEntity();
        mockMatch.setId(1L);
        mockMatch.setLeague("УПЛ");
        mockMatch.setHomeTeam(homeTeam);
        mockMatch.setAwayTeam(awayTeam);
        mockMatch.setKickoffAt(java.time.LocalDateTime.of(2025, 11, 15, 19, 0));
        
        when(matchDbService.create(
                org.mockito.ArgumentMatchers.anyString(), 
                org.mockito.ArgumentMatchers.anyString(), 
                org.mockito.ArgumentMatchers.any(), 
                org.mockito.ArgumentMatchers.anyString()
        )).thenReturn(mockMatch);

        String jsonBody = """
                {
                    "homeTeam": "Динамо",
                    "awayTeam": "Шахтар",
                    "kickoffAt": "2025-11-15T19:00:00",
                    "league": "УПЛ"
                }
                """;

        mockMvc.perform(post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated());
    }
}
