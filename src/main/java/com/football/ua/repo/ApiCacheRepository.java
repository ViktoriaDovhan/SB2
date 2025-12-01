package com.football.ua.repo;

import com.football.ua.model.entity.ApiCacheEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ApiCacheRepository extends JpaRepository<ApiCacheEntity, Long> {
    
    Optional<ApiCacheEntity> findByCategoryAndKey(String category, String key);
    
    List<ApiCacheEntity> findByCategory(String category);
    
    long countByCategory(String category);
    
    @Modifying
    @Query("DELETE FROM ApiCacheEntity a WHERE a.category = ?1 AND a.key = ?2")
    void deleteByCategoryAndKey(String category, String key);
    
    @Modifying
    @Query("DELETE FROM ApiCacheEntity a WHERE a.category = ?1")
    void deleteByCategory(String category);
    
    @Modifying
    @Query("DELETE FROM ApiCacheEntity a WHERE a.timestamp < ?1")
    void deleteExpiredBefore(LocalDateTime timestamp);
}
