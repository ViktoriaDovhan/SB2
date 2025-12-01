package com.football.ua.repo;

import com.football.ua.model.entity.StandingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StandingRepository extends JpaRepository<StandingEntity, Long> {
    
    List<StandingEntity> findByLeague(String league);
    
    List<StandingEntity> findByLeagueOrderByPositionAsc(String league);
    
    void deleteByLeague(String league);
    
    Optional<StandingEntity> findByTeamNameAndLeague(String teamName, String league);
    
    @Query("SELECT COUNT(s) FROM StandingEntity s WHERE s.league = :league")
    long countByLeague(@Param("league") String league);
}
