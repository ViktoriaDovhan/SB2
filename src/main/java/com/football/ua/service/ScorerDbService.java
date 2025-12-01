package com.football.ua.service;

import com.football.ua.model.entity.ScorerEntity;

import java.util.List;

public interface ScorerDbService {
    ScorerEntity save(ScorerEntity scorer);
    void saveAll(List<ScorerEntity> scorers);
    List<ScorerEntity> list();
    List<ScorerEntity> listByLeague(String league);
    void deleteAll();
    void deleteByLeague(String league);
    long count();
    long countByLeague(String league);
}
