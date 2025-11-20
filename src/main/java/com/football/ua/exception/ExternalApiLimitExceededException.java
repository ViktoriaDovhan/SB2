package com.football.ua.exception;

public class ExternalApiLimitExceededException extends RuntimeException {

    public ExternalApiLimitExceededException(String message) {
        super(message);
    }
}
