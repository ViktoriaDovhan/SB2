package com.football.ua.repo;

import com.football.ua.model.entity.TeamEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<TeamEntity, Long> {


    Optional<TeamEntity> findByName(String name);
    boolean existsByName(String name);

    List<TeamEntity> findByLeague(String league);
    List<TeamEntity> findByLeagueAndActiveTrue(String league);
    List<TeamEntity> findByActiveTrue();

    @Modifying
    @Query("UPDATE TeamEntity t SET t.city = :city, t.colors = :colors, t.emblemUrl = :emblemUrl, t.lastUpdated = :lastUpdated WHERE t.id = :id")
    int updateTeamDetails(@Param("id") Long id, @Param("city") String city, @Param("colors") String colors, @Param("emblemUrl") String emblemUrl, @Param("lastUpdated") LocalDateTime lastUpdated);

    @Modifying
    @Query("UPDATE TeamEntity t SET t.active = false WHERE t.league = :league AND t.lastUpdated < :cutoffDate")
    int deactivateOldTeams(@Param("league") String league, @Param("cutoffDate") LocalDateTime cutoffDate);

    Optional<TeamEntity> findByNameAndLeague(String name, String league);

    @Query("SELECT t FROM TeamEntity t WHERE t.lastUpdated < :cutoffDate AND t.active = true")
    List<TeamEntity> findTeamsNeedingUpdate(@Param("cutoffDate") LocalDateTime cutoffDate);
}



