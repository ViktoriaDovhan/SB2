package com.football.ua.controller;

import com.football.ua.exception.BadRequestException;
import com.football.ua.exception.NotFoundException;
import com.football.ua.model.News;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/news")
public class NewsController {
    private static final Map<Long, News> db = new LinkedHashMap<>();
    private static long idSeq = 1;

    @GetMapping
    public List<News> list() { return new ArrayList<>(db.values()); }

    @GetMapping("/{id}")
    public News one(@PathVariable Long id) {
        var n = db.get(id);
        if (n == null) throw new NotFoundException("News not found");
        return n;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public News create(@Valid @RequestBody News body) {
        if (body.title == null || body.content == null)
            throw new BadRequestException("Title and content required");
        body.id = idSeq++; body.likes = 0;
        db.put(body.id, body);
        return body;
    }

    @PutMapping("/{id}")
    public News update(@PathVariable Long id, @Valid @RequestBody News body) {
        var n = db.get(id);
        if (n == null) throw new NotFoundException("News not found");
        n.title = body.title; n.content = body.content;
        return n;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (db.remove(id) == null) throw new NotFoundException("News not found");
    }

    @PostMapping("/{id}/like")
    public News like(@PathVariable Long id) {
        var n = db.get(id);
        if (n == null) throw new NotFoundException("News not found");
        n.likes += 1; return n;
    }
}
