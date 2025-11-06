package com.football.ua.controller;

import com.football.ua.model.ActivityLog;
import com.football.ua.service.ActivityLogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/activity")
public class ActivityController {
    
    private final ActivityLogService activityLogService;
    
    public ActivityController(ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }
    
    @GetMapping
    public List<ActivityLog> getRecent(@RequestParam(defaultValue = "50") int limit) {
        return activityLogService.getRecentActivities(limit);
    }
    
    @GetMapping("/category/{category}")
    public List<ActivityLog> getByCategory(
            @PathVariable String category, 
            @RequestParam(defaultValue = "50") int limit) {
        return activityLogService.getActivitiesByCategory(category, limit);
    }
}

