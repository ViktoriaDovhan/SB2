package com.football.ua.service;

import com.football.ua.model.ActivityLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class ActivityLogService {
    
    private static final Logger log = LoggerFactory.getLogger(ActivityLogService.class);
    private static final Marker ACTIVITY = MarkerFactory.getMarker("ACTIVITY");
    
    private final ConcurrentLinkedQueue<ActivityLog> recentActivities = new ConcurrentLinkedQueue<>();
    private static final int MAX_ACTIVITIES = 100;
    
    public void logActivity(String action, String details, String category) {
        ActivityLog activity = new ActivityLog(action, details, category);
        activity.id = (long) (recentActivities.size() + 1);
        
        MDC.put("activity", action);
        MDC.put("category", category);
        try {
            log.info(ACTIVITY, "{}: {}", action, details);
        } finally {
            MDC.clear();
        }
        
        recentActivities.add(activity);
        
        if (recentActivities.size() > MAX_ACTIVITIES) {
            recentActivities.poll();
        }
    }
    
    public List<ActivityLog> getRecentActivities(int limit) {
        List<ActivityLog> activities = new ArrayList<>(recentActivities);
        Collections.reverse(activities);
        return activities.stream()
                .limit(limit)
                .toList();
    }
    
    public List<ActivityLog> getActivitiesByCategory(String category, int limit) {
        List<ActivityLog> activities = new ArrayList<>(recentActivities);
        Collections.reverse(activities);
        return activities.stream()
                .filter(a -> category.equals(a.category))
                .limit(limit)
                .toList();
    }
}

