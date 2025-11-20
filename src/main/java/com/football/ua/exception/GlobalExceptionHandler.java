package com.football.ua.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.stream.Collectors;
import com.football.ua.exception.ExternalApiLimitExceededException;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String,Object>> notFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error","NOT_FOUND","message",ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String,Object>> badRequest(BadRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error","BAD_REQUEST","message",ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String,Object>> validation(MethodArgumentNotValidException ex) {
        var fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(f -> f.getField(), f -> f.getDefaultMessage(), (a,b)->a));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error","VALIDATION_FAILED","fields", fields));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String,Object>> accessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error","ACCESS_DENIED","message",ex.getMessage()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String,Object>> rateLimit(RateLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "error", "RATE_LIMIT_EXCEEDED",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ExternalApiLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> externalApiLimit(ExternalApiLimitExceededException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of(
                        "error", "EXTERNAL_API_RATE_LIMIT",
                        "message", ex.getMessage()
                ));
    }

}
