package com.football.ua.aspect;

import com.football.ua.service.ActivityLogService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ExceptionHandlingAspect {

    private static final Logger log = LoggerFactory.getLogger(ExceptionHandlingAspect.class);

    private final ActivityLogService activityLogService;

    public ExceptionHandlingAspect(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @AfterThrowing(
            pointcut = "within(com.football.ua.controller..*) || within(com.football.ua.service..*)",
            throwing = "ex"
    )
    public void logException(JoinPoint joinPoint, Throwable ex) {
        String method = joinPoint.getSignature().toShortString();

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null && authentication.isAuthenticated()
                ? authentication.getName()
                : "anonymous";

        log.error("Помилка у методі {} для користувача {}: {}", method, username, ex.getMessage(), ex);

        activityLogService.logActivity(
                "Помилка в застосунку",
                "Метод " + method + " кинув " + ex.getClass().getSimpleName() + ": " + ex.getMessage(),
                "ERROR"
        );
    }
}
