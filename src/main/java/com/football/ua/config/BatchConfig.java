package com.football.ua.config;

import com.football.ua.service.DataMigrationService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class BatchConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataMigrationService dataMigrationService;

    public BatchConfig(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            DataMigrationService dataMigrationService
    ) {
        this.jobRepository = jobRepository;
        this.transactionManager = transactionManager;
        this.dataMigrationService = dataMigrationService;
    }

    @Bean
    public Step migrateTeamsStep() {
        return new StepBuilder("migrateTeamsStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // 1 крок міграція команд з API в БД
                    dataMigrationService.migrateTeamsFromCacheToDatabase();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    @Bean
    public Step migrateMatchesStep() {
        return new StepBuilder("migrateMatchesStep", jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    // 2 крок завантаження матчів з API для всіх ліг
                    dataMigrationService.migrateMatchesForAllLeagues();
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }


    @Bean
    public Job cacheToDatabaseJob(
            Step migrateTeamsStep,
            Step migrateMatchesStep
    ) {
        return new JobBuilder("cacheToDatabaseJob", jobRepository)
                .start(migrateTeamsStep)
                .next(migrateMatchesStep)
                .build();
    }
}
