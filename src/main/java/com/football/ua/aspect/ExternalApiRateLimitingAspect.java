package com.football.ua.aspect;

import com.football.ua.exception.ExternalApiLimitExceededException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

@Aspect
@Component
public class ExternalApiRateLimitingAspect {

    private static final Logger log = LoggerFactory.getLogger(ExternalApiRateLimitingAspect.class);

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int MAX_CALLS_PER_MINUTE = 10;

    // Глобальна черга викликів до зовнішнього API (для всього застосунку)
    private final Deque<Instant> apiCalls = new ArrayDeque<>();

    @Around("@annotation(com.football.ua.aspect.ExternalApiCall)")
    public Object enforceExternalApiLimit(ProceedingJoinPoint joinPoint) throws Throwable {
        Instant now = Instant.now();

        synchronized (apiCalls) {
            // прибираємо всі виклики старші за 1 хвилину
            while (!apiCalls.isEmpty() && apiCalls.peekFirst().isBefore(now.minus(WINDOW))) {
                apiCalls.pollFirst();
            }

            if (apiCalls.size() >= MAX_CALLS_PER_MINUTE) {
                log.warn("Перевищено ліміт {} викликів до зовнішнього API за хвилину", MAX_CALLS_PER_MINUTE);
                throw new ExternalApiLimitExceededException(
                        "Перевищено ліміт запитів до зовнішнього football API (максимум "
                                + MAX_CALLS_PER_MINUTE + " на хвилину)"
                );
            }

            // реєструємо новий виклик
            apiCalls.addLast(now);
        }

        // якщо ліміт не перевищено – реально викликаємо метод
        return joinPoint.proceed();
    }
}
