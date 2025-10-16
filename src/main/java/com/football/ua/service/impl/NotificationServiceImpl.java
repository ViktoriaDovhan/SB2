package com.football.ua.service.impl;

import com.football.ua.service.NotificationService;
import com.football.starter.notification.NotificationSender;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private Clock clock;
    
    @Autowired(required = false)
    private NotificationSender notificationSender;

    @PostConstruct
    public void init() {
        System.out.println("[NotificationService] ready at " + clock.instant());
        if (notificationSender != null) {
            System.out.println("✅ NotificationSender з стартера підключено!");
        } else {
            System.out.println("⚠️ NotificationSender з стартера не доступний");
        }
    }

    @PreDestroy
    public void shutdown() {
        System.out.println("[NotificationService] shutting down at " + clock.instant());
    }

    @Override
    public void notifyAll(String channel, String message) {

        if (notificationSender != null) {
            notificationSender.send(channel.toUpperCase(), message);
        } else {

            System.out.println("[" + clock.instant() + "] [" + channel + "] " + message);
        }
    }
}
