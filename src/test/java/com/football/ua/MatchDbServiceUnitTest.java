package com.football.ua;

import com.football.ua.model.entity.MatchEntity;
import com.football.ua.model.entity.TeamEntity;
import com.football.ua.repo.MatchRepository;
import com.football.ua.repo.TeamRepository;
import com.football.ua.service.impl.MatchDbServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit тести бізнес-логіки")
public class MatchDbServiceUnitTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private MatchDbServiceImpl matchDbService;

    private TeamEntity mockHomeTeam;
    private TeamEntity mockAwayTeam;

    @BeforeEach
    public void setUp() {
        mockHomeTeam = new TeamEntity();
        mockHomeTeam.setId(1L);
        mockHomeTeam.setName("Динамо");

        mockAwayTeam = new TeamEntity();
        mockAwayTeam.setId(2L);
        mockAwayTeam.setName("Шахтар");
    }

    @Test
    @DisplayName("Створення матчу з новими командами")
    public void testCreateMatch_WithNewTeams() {
        String homeTeamName = "Нова Команда А";
        String awayTeamName = "Нова Команда Б";
        LocalDateTime kickoff = LocalDateTime.of(2025, 12, 1, 15, 0);
        String league = "УПЛ";

        when(teamRepository.findByName(homeTeamName)).thenReturn(Optional.empty());
        when(teamRepository.findByName(awayTeamName)).thenReturn(Optional.empty());

        TeamEntity savedHome = new TeamEntity();
        savedHome.setId(10L);
        savedHome.setName(homeTeamName);

        TeamEntity savedAway = new TeamEntity();
        savedAway.setId(20L);
        savedAway.setName(awayTeamName);

        when(teamRepository.save(any(TeamEntity.class)))
                .thenReturn(savedHome)
                .thenReturn(savedAway);

        MatchEntity savedMatch = new MatchEntity();
        savedMatch.setId(100L);
        savedMatch.setHomeTeam(savedHome);
        savedMatch.setAwayTeam(savedAway);
        savedMatch.setKickoffAt(kickoff);
        savedMatch.setLeague(league);

        when(matchRepository.save(any(MatchEntity.class))).thenReturn(savedMatch);

        MatchEntity result = matchDbService.create(homeTeamName, awayTeamName, kickoff, league);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getHomeTeam().getName()).isEqualTo(homeTeamName);
        assertThat(result.getAwayTeam().getName()).isEqualTo(awayTeamName);
        assertThat(result.getHomeScore()).isNull();
        assertThat(result.getAwayScore()).isNull();

        verify(teamRepository, times(2)).save(any(TeamEntity.class));
        verify(matchRepository, times(1)).save(any(MatchEntity.class));
    }

    @Test
    @DisplayName("Створення матчу з існуючими командами")
    public void testCreateMatch_WithExistingTeams() {
        LocalDateTime kickoff = LocalDateTime.of(2025, 12, 5, 18, 0);
        String league = "УПЛ";

        when(teamRepository.findByName("Динамо")).thenReturn(Optional.of(mockHomeTeam));
        when(teamRepository.findByName("Шахтар")).thenReturn(Optional.of(mockAwayTeam));

        MatchEntity savedMatch = new MatchEntity();
        savedMatch.setId(200L);
        savedMatch.setHomeTeam(mockHomeTeam);
        savedMatch.setAwayTeam(mockAwayTeam);
        savedMatch.setKickoffAt(kickoff);
        savedMatch.setLeague(league);

        when(matchRepository.save(any(MatchEntity.class))).thenReturn(savedMatch);

        MatchEntity result = matchDbService.create("Динамо", "Шахтар", kickoff, league);

        assertThat(result).isNotNull();
        assertThat(result.getHomeTeam().getId()).isEqualTo(1L);
        assertThat(result.getAwayTeam().getId()).isEqualTo(2L);

        verify(teamRepository, never()).save(any(TeamEntity.class));
        verify(matchRepository, times(1)).save(any(MatchEntity.class));
    }

    @Test
    @DisplayName("Оновлення рахунку матчу")
    public void testUpdateScore() {
        Long matchId = 1L;
        Integer newHomeScore = 3;
        Integer newAwayScore = 1;

        MatchEntity existingMatch = new MatchEntity();
        existingMatch.setId(matchId);
        existingMatch.setHomeTeam(mockHomeTeam);
        existingMatch.setAwayTeam(mockAwayTeam);
        existingMatch.setHomeScore(null);
        existingMatch.setAwayScore(null);
        existingMatch.setLeague("УПЛ");

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(existingMatch));
        when(matchRepository.save(existingMatch)).thenReturn(existingMatch);

        MatchEntity result = matchDbService.updateScore(matchId, newHomeScore, newAwayScore);

        assertThat(result).isNotNull();
        assertThat(result.getHomeScore()).isEqualTo(newHomeScore);
        assertThat(result.getAwayScore()).isEqualTo(newAwayScore);

        verify(matchRepository, times(1)).findById(matchId);
        verify(matchRepository, times(1)).save(existingMatch);
    }
}
