package com.football.ua.repo;

import com.football.ua.model.entity.ScorerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ScorerRepository extends JpaRepository<ScorerEntity, Long> {
    
    List<ScorerEntity> findByLeague(String league);
    
    List<ScorerEntity> findByLeagueOrderByGoalsDesc(String league);
    
    void deleteByLeague(String league);
    
    Optional<ScorerEntity> findByPlayerIdAndLeague(Integer playerId, String league);
    
    @Query("SELECT COUNT(s) FROM ScorerEntity s WHERE s.league = :league")
    long countByLeague(@Param("league") String league);
}
