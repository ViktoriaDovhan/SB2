package com.football.ua.service.impl;

import com.football.ua.model.entity.StandingEntity;
import com.football.ua.repo.StandingRepository;
import com.football.ua.service.StandingDbService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StandingDbServiceImpl implements StandingDbService {
    
    private static final Logger log = LoggerFactory.getLogger(StandingDbServiceImpl.class);
    
    private final StandingRepository standingRepository;
    
    public StandingDbServiceImpl(StandingRepository standingRepository) {
        this.standingRepository = standingRepository;
    }
    
    @Override
    @Transactional
    public StandingEntity save(StandingEntity standing) {
        return standingRepository.save(standing);
    }
    
    @Override
    @Transactional
    public void saveAll(List<StandingEntity> standings) {
        standingRepository.saveAll(standings);
        log.info("💾 Збережено {} позицій турнірної таблиці в БД", standings.size());
    }
    
    @Override
    public List<StandingEntity> list() {
        return standingRepository.findAll();
    }
    
    @Override
    public List<StandingEntity> listByLeague(String league) {
        return standingRepository.findByLeagueOrderByPositionAsc(league);
    }
    
    @Override
    @Transactional
    public void deleteAll() {
        standingRepository.deleteAll();
        log.info("🗑️ Видалено всі турнірні таблиці з БД");
    }
    
    @Override
    @Transactional
    public void deleteByLeague(String league) {
        standingRepository.deleteByLeague(league);
        log.info("🗑️ Видалено турнірну таблицю для ліги {} з БД", league);
    }
    
    @Override
    public long count() {
        return standingRepository.count();
    }
    
    @Override
    public long countByLeague(String league) {
        return standingRepository.countByLeague(league);
    }
}
