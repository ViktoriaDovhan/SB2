package com.football.ua.service;

import com.football.ua.model.entity.StandingEntity;

import java.util.List;

public interface StandingDbService {
    StandingEntity save(StandingEntity standing);
    void saveAll(List<StandingEntity> standings);
    List<StandingEntity> list();
    List<StandingEntity> listByLeague(String league);
    void deleteAll();
    void deleteByLeague(String league);
    long count();
    long countByLeague(String league);
}
