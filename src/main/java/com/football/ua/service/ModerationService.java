package com.football.ua.service;

public interface ModerationService {
    boolean containsProfanity(String text);
    String moderate(String text);
}