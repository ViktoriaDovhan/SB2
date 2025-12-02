package com.football.ua.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BatchJobScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchJobScheduler.class);

    private final JobLauncher jobLauncher;
    private final Job cacheToDatabaseJob;

    public BatchJobScheduler(
            JobLauncher jobLauncher,
            @Qualifier("cacheToDatabaseJob") Job cacheToDatabaseJob
    ) {
        this.jobLauncher = jobLauncher;
        this.cacheToDatabaseJob = cacheToDatabaseJob;
    }

    /**
     * Плановий запуск job один раз на добу о 03:00 ночі.
     * cron формат: секунда хвилина година день-місяця місяць день-тижня
     */
    @Scheduled(cron = "${batch.cache-to-db.cron:0 0 3 * * *}")
    public void runCacheToDatabaseJobNightly() {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("trigger", "scheduler")
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(cacheToDatabaseJob, params);
            log.info("✅ Плановий запуск cacheToDatabaseJob завершився зі статусом {}", execution.getStatus());
        } catch (Exception e) {
            log.error("❌ Помилка під час планового запуску cacheToDatabaseJob", e);
        }
    }
}
