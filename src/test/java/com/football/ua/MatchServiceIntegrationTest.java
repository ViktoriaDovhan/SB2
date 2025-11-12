package com.football.ua;

import com.football.ua.model.entity.MatchEntity;
import com.football.ua.repo.MatchRepository;
import com.football.ua.repo.TeamRepository;
import com.football.ua.service.MatchDbService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Інтеграційний тест з SpringBootTest")
public class MatchServiceIntegrationTest {

    @Autowired
    private MatchDbService matchDbService;

    @Autowired
    private MatchRepository matchRepository;

    @Autowired
    private TeamRepository teamRepository;

    @BeforeEach
    public void setUp() {
        matchRepository.deleteAll();
        teamRepository.deleteAll();
    }

    @AfterEach
    public void tearDown() {
        matchRepository.deleteAll();
        teamRepository.deleteAll();
    }

    @Test
    @DisplayName("Створення матчу")
    @Transactional
    public void testCreateMatchWithSpringBootTest() {
        String homeTeam = "Динамо Київ";
        String awayTeam = "Шахтар Донецьк";
        LocalDateTime kickoffAt = LocalDateTime.of(2025, 12, 1, 15, 0);
        String league = "УПЛ";

        MatchEntity createdMatch = matchDbService.create(homeTeam, awayTeam, kickoffAt, league);

        assertThat(createdMatch).isNotNull();
        assertThat(createdMatch.getId()).isNotNull();
        assertThat(createdMatch.getHomeTeam().getName()).isEqualTo(homeTeam);
        assertThat(createdMatch.getAwayTeam().getName()).isEqualTo(awayTeam);
        assertThat(createdMatch.getKickoffAt()).isEqualTo(kickoffAt);
        assertThat(createdMatch.getLeague()).isEqualTo(league);

        assertThat(matchRepository.count()).isEqualTo(1);
        assertThat(teamRepository.count()).isEqualTo(2);
    }
}
