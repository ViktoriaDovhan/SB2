package com.football.ua.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class ManualCacheJobRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ManualCacheJobRunner.class);

    private final JobLauncher jobLauncher;
    private final Job cacheToDatabaseJob;

    @Value("${batch.cache-to-db.run-on-startup:false}")
    private boolean runOnStartup;

    public ManualCacheJobRunner(
            JobLauncher jobLauncher,
            @Qualifier("cacheToDatabaseJob") Job cacheToDatabaseJob
    ) {
        this.jobLauncher = jobLauncher;
        this.cacheToDatabaseJob = cacheToDatabaseJob;
    }

    @Override
    public void run(String... args) {
        if (!runOnStartup) {
            log.info("Manual run of cacheToDatabaseJob is disabled (batch.cache-to-db.run-on-startup=false)");
            return;
        }

        log.info("Manual run of cacheToDatabaseJob on application startup...");

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("timestamp", System.currentTimeMillis())
                    .addString("trigger", "manual-startup")
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(cacheToDatabaseJob, params);

            log.info("Manual run of cacheToDatabaseJob finished with status {} (executionId={})",
                    execution.getStatus(), execution.getId());
        } catch (Exception e) {
            log.error("Error during manual run of cacheToDatabaseJob on startup", e);
        }
    }
}
