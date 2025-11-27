package com.football.ua.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {
    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final int MAX_PERMITS = 1; 

    private static final int PERIOD_SECONDS = 6; 
    
    private final Semaphore semaphore = new Semaphore(MAX_PERMITS);
    
    public RateLimiterService() {

        Thread replenisher = new Thread(() -> {
            while (true) {
                try {

                    Thread.sleep(PERIOD_SECONDS * 1000);
                    if (semaphore.availablePermits() < MAX_PERMITS) {
                        semaphore.release();
                        log.trace("Token replenished. Available: {}", semaphore.availablePermits());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        replenisher.setDaemon(true);
        replenisher.start();
    }
    
    public void acquire() {
        try {
            log.debug("Acquiring API token... Available: {}", semaphore.availablePermits());

            if (!semaphore.tryAcquire(60, TimeUnit.SECONDS)) {
                log.warn("Timeout waiting for API token. Proceeding anyway but risk of 429.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for API token");
        }
    }
}

