package com.football.ua.model.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "api_cache", indexes = {
    @Index(name = "idx_category_key", columnList = "category, cache_key", unique = true)
})
public class ApiCacheEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String category;
    
    @Column(name = "cache_key", nullable = false, length = 255)
    private String key;
    
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String data;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(nullable = false)
    private Long durationMinutes;
    
    public ApiCacheEntity(String category, String key, String data, LocalDateTime timestamp, Long durationMinutes) {
        this.category = category;
        this.key = key;
        this.data = data;
        this.timestamp = timestamp;
        this.durationMinutes = durationMinutes;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public Long getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Long durationMinutes) {
        this.durationMinutes = durationMinutes;
    }
}
