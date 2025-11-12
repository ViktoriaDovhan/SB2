package com.football.ua;

import com.football.ua.controller.MatchController;
import com.football.ua.model.entity.MatchEntity;
import com.football.ua.model.entity.TeamEntity;
import com.football.ua.service.ActivityLogService;
import com.football.ua.service.MatchDbService;
import com.football.ua.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MatchController.class)
@DisplayName("WebMvcTest API тест")
public class MatchControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MatchDbService matchDbService;

    @MockBean
    private ActivityLogService activityLogService;

    @MockBean
    private JwtUtil jwtUtil;

    private MatchEntity mockMatch1;
    private MatchEntity mockMatch2;

    @BeforeEach
    public void setUp() {
        TeamEntity dynamoTeam = new TeamEntity();
        dynamoTeam.setId(1L);
        dynamoTeam.setName("Динамо");
        
        TeamEntity shaktarTeam = new TeamEntity();
        shaktarTeam.setId(2L);
        shaktarTeam.setName("Шахтар");

        TeamEntity dniproTeam = new TeamEntity();
        dniproTeam.setId(3L);
        dniproTeam.setName("Дніпро");

        mockMatch1 = new MatchEntity();
        mockMatch1.setId(1L);
        mockMatch1.setHomeTeam(dynamoTeam);
        mockMatch1.setAwayTeam(shaktarTeam);
        mockMatch1.setKickoffAt(LocalDateTime.of(2025, 11, 20, 19, 0));
        mockMatch1.setLeague("УПЛ");
        mockMatch1.setHomeScore(2);
        mockMatch1.setAwayScore(1);

        mockMatch2 = new MatchEntity();
        mockMatch2.setId(2L);
        mockMatch2.setHomeTeam(dniproTeam);
        mockMatch2.setAwayTeam(dynamoTeam);
        mockMatch2.setKickoffAt(LocalDateTime.of(2025, 11, 25, 18, 30));
        mockMatch2.setLeague("УПЛ");
        mockMatch2.setHomeScore(null);
        mockMatch2.setAwayScore(null);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/matches повертає список матчів з коректною структурою JSON")
    public void testGetAllMatches() throws Exception {
        List<MatchEntity> matches = Arrays.asList(mockMatch1, mockMatch2);
        when(matchDbService.list()).thenReturn(matches);

        mockMvc.perform(get("/api/matches"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(1)))
                .andExpect(jsonPath("$[0].homeTeam", is("Динамо")))
                .andExpect(jsonPath("$[0].awayTeam", is("Шахтар")))
                .andExpect(jsonPath("$[0].homeScore", is(2)))
                .andExpect(jsonPath("$[0].awayScore", is(1)))
                .andExpect(jsonPath("$[0].league", is("УПЛ")))
                .andExpect(jsonPath("$[0].kickoffAt", is("2025-11-20T19:00:00")))
                .andExpect(jsonPath("$[1].id", is(2)))
                .andExpect(jsonPath("$[1].homeTeam", is("Дніпро")))
                .andExpect(jsonPath("$[1].awayTeam", is("Динамо")))
                .andExpect(jsonPath("$[1].homeScore", nullValue()))
                .andExpect(jsonPath("$[1].awayScore", nullValue()))
                .andExpect(jsonPath("$[1].league", is("УПЛ")))
                .andExpect(jsonPath("$[1].kickoffAt", is("2025-11-25T18:30:00")));
    }
}
