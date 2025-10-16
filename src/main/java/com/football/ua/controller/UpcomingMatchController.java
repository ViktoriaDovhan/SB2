package com.football.ua.controller;

import com.football.ua.service.UpcomingMatchNotificationService;
import com.football.ua.service.UpcomingMatchNotificationService.UpcomingMatchNotification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


@RestController
@RequestMapping("/api/upcoming-matches")
public class UpcomingMatchController {
    
    private final UpcomingMatchNotificationService upcomingMatchService;
    
    public UpcomingMatchController(UpcomingMatchNotificationService upcomingMatchService) {
        this.upcomingMatchService = upcomingMatchService;
    }
    
    @GetMapping
    public List<UpcomingMatchNotification> getUpcomingMatches() {
        return upcomingMatchService.getUpcomingMatches();
    }
}


