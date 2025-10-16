package com.football.ua.controller;

import com.football.ua.service.impl.PlayerOfTheWeekServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class PlayerOfTheWeekController {

    private final PlayerOfTheWeekServiceImpl playerOfTheWeekService;

    @Autowired
    public PlayerOfTheWeekController(@Autowired(required = false) PlayerOfTheWeekServiceImpl playerOfTheWeekService) {
        this.playerOfTheWeekService = playerOfTheWeekService;
    }

    @GetMapping("/api/player-of-the-week")
    public ResponseEntity<Map<String, Object>> getPlayer() {
        if (playerOfTheWeekService == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(playerOfTheWeekService.getPlayerData());
    }
}