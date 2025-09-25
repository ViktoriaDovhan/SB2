package com.example.fittrack.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import java.time.LocalDateTime;

@Getter
@RequiredArgsConstructor
public class ApiError {
    private final String message;
    private final int status;
    private final LocalDateTime time = LocalDateTime.now();

}
