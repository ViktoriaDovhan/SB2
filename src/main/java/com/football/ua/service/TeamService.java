package com.football.ua.service;

public interface TeamService {
    boolean exists(String name);
    void addTeam(String name);
}