package com.football.ua.aspect;

import com.football.ua.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Aspect
@Component
public class RateLimitingAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingAspect.class);

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Map<String, Deque<Instant>> calls = new ConcurrentHashMap<>();

    private final HttpServletRequest request;

    public RateLimitingAspect(HttpServletRequest request) {
        this.request = request;
    }

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String userKey = resolveUserKey();
        String methodKey = ((MethodSignature) joinPoint.getSignature()).toShortString();
        String key = userKey + ":" + methodKey;

        int maxCalls = rateLimited.value();
        Instant now = Instant.now();

        Deque<Instant> timestamps = calls.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(now.minus(WINDOW))) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= maxCalls) {
                log.warn("Перевищено ліміт {} викликів за хвилину для ключа {}", maxCalls, key);
                throw new RateLimitExceededException("Перевищено ліміт запитів для поточного користувача");
            }

            timestamps.addLast(now);
        }

        return joinPoint.proceed();
    }

    private String resolveUserKey() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return "user:" + authentication.getName();
        }
        String ip = request != null ? request.getRemoteAddr() : "unknown";
        return "anon:" + ip;
    }
}
