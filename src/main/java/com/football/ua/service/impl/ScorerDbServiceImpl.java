package com.football.ua.service.impl;

import com.football.ua.model.entity.ScorerEntity;
import com.football.ua.repo.ScorerRepository;
import com.football.ua.service.ScorerDbService;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScorerDbServiceImpl implements ScorerDbService {
    
    private static final Logger log = LoggerFactory.getLogger(ScorerDbServiceImpl.class);
    
    private final ScorerRepository scorerRepository;
    
    public ScorerDbServiceImpl(ScorerRepository scorerRepository) {
        this.scorerRepository = scorerRepository;
    }
    
    @Override
    @Transactional
    public ScorerEntity save(ScorerEntity scorer) {
        return scorerRepository.save(scorer);
    }
    
    @Override
    @Transactional
    public void saveAll(List<ScorerEntity> scorers) {
        scorerRepository.saveAll(scorers);
        log.info("💾 Збережено {} бомбардирів в БД", scorers.size());
    }
    
    @Override
    public List<ScorerEntity> list() {
        return scorerRepository.findAll();
    }
    
    @Override
    public List<ScorerEntity> listByLeague(String league) {
        return scorerRepository.findByLeagueOrderByGoalsDesc(league);
    }
    
    @Override
    @Transactional
    public void deleteAll() {
        scorerRepository.deleteAll();
        log.info("🗑️ Видалено всіх бомбардирів з БД");
    }
    
    @Override
    @Transactional
    public void deleteByLeague(String league) {
        scorerRepository.deleteByLeague(league);
        log.info("🗑️ Видалено бомбардирів для ліги {} з БД", league);
    }
    
    @Override
    public long count() {
        return scorerRepository.count();
    }
    
    @Override
    public long countByLeague(String league) {
        return scorerRepository.countByLeague(league);
    }
}
