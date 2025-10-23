package com.football.ua.controller;

import com.football.ua.exception.BadRequestException;
import com.football.ua.exception.NotFoundException;
import com.football.ua.model.News;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/news")
@Tag(name = "News", description = "API для управління новинами")
public class NewsController {
    private static final Map<Long, News> db = new LinkedHashMap<>();
    private static long idSeq = 1;
    
    private final com.football.ua.service.ActivityLogService activityLogService;
    
    public NewsController(com.football.ua.service.ActivityLogService activityLogService) {
        this.activityLogService = activityLogService;
    }

    @GetMapping
    @Operation(summary = "Отримати всі новини", description = "Повертає список всіх новин")
    public List<News> list() { return new ArrayList<>(db.values()); }

    @GetMapping("/{id}")
    @Operation(summary = "Отримати новину за ID", description = "Повертає конкретну новину за її ідентифікатором")
    public News one(@PathVariable Long id) {
        var n = db.get(id);
        if (n == null) throw new NotFoundException("News not found");
        return n;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Створити новину", description = "Створює нову новину")
    public News create(@Valid @RequestBody News body) {
        if (body.title == null || body.content == null)
            throw new BadRequestException("Title and content required");
        body.id = idSeq++; body.likes = 0;
        db.put(body.id, body);
        
        activityLogService.logActivity(
            "Опубліковано нову новину",
            String.format("\"%s\"", body.title),
            "NEWS"
        );
        
        return body;
    }

    @PutMapping("/{id}")
    public News update(@PathVariable Long id, @Valid @RequestBody News body) {
        var n = db.get(id);
        if (n == null) throw new NotFoundException("News not found");
        n.title = body.title; n.content = body.content;
        
        activityLogService.logActivity(
            "Оновлено новину",
            String.format("Новина #%d: \"%s\"", id, body.title),
            "NEWS"
        );
        
        return n;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        News removed = db.remove(id);
        if (removed == null) throw new NotFoundException("News not found");
        
        activityLogService.logActivity(
            "Видалено новину",
            String.format("Новина #%d видалена редактором", id),
            "NEWS"
        );
    }

    @PostMapping("/{id}/like")
    public News like(@PathVariable Long id) {
        var n = db.get(id);
        if (n == null) throw new NotFoundException("News not found");
        n.likes += 1;
        
        activityLogService.logActivity(
            "Вподобано новину",
            String.format("\"%s\" (всього: %d ❤️)", n.title, n.likes),
            "NEWS"
        );
        
        return n;
    }
}
