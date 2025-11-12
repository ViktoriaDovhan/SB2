package com.football.ua;

import com.football.ua.model.entity.MatchEntity;
import com.football.ua.repo.MatchRepository;
import com.football.ua.repo.TeamRepository;
import com.football.ua.service.MatchDbService;
import com.football.ua.service.impl.MatchDbServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@Import(MatchDbServiceImpl.class)
@ActiveProfiles("test")
@DisplayName("Тест з Import")
public class MatchServiceImportTest {

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

    @Test
    @DisplayName("Сервіс працює через Import")
    public void testServiceWorksWithImport() {
        LocalDateTime kickoff = LocalDateTime.of(2025, 12, 1, 15, 0);

        MatchEntity created = matchDbService.create("Динамо", "Шахтар", kickoff, "УПЛ");

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getHomeTeam().getName()).isEqualTo("Динамо");
        assertThat(created.getAwayTeam().getName()).isEqualTo("Шахтар");
        
        assertThat(matchRepository.count()).isEqualTo(1);
        assertThat(teamRepository.count()).isEqualTo(2);
    }
}
