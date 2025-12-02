package com.football.ua.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/batch")
@Tag(name = "Batch jobs", description = "Ручний запуск Spring Batch завдань")
public class BatchJobController {

    private final JobLauncher jobLauncher;
    private final Job cacheToDatabaseJob;

    public BatchJobController(
            JobLauncher jobLauncher,
            @Qualifier("cacheToDatabaseJob") Job cacheToDatabaseJob
    ) {
        this.jobLauncher = jobLauncher;
        this.cacheToDatabaseJob = cacheToDatabaseJob;
    }

    @PostMapping("/cache-to-db/run")
    @Operation(summary = "Ручний запуск job міграції кешу в базу даних")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<String> runCacheToDatabaseJob() throws Exception {

        JobParameters params = new JobParametersBuilder()
                .addLong("timestamp", System.currentTimeMillis())
                .addString("trigger", "manual")
                .toJobParameters();

        JobExecution execution = jobLauncher.run(cacheToDatabaseJob, params);

        String body = "Job cacheToDatabaseJob запущено. Статус = "
                + execution.getStatus()
                + ", executionId = "
                + execution.getId();

        return ResponseEntity.ok(body);
    }
}
