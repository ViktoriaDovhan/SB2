package com.football.ua.model;

import java.time.LocalDateTime;

public class ActivityLog {
    public Long id;
    public String action;
    public String details;
    public String userName;
    public String category;
    public LocalDateTime timestamp;
    
    public ActivityLog() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ActivityLog(String action, String details, String category) {
        this.action = action;
        this.details = details;
        this.category = category;
        this.timestamp = LocalDateTime.now();
        this.userName = "Користувач";
    }
}

