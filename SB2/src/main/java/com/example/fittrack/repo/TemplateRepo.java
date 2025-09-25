package com.example.fittrack.repo;

import com.example.fittrack.model.Template;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TemplateRepo {
    private final Map<Long, Template> db = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    public List<Template> findAll() { return new ArrayList<>(db.values()); }

    public Optional<Template> findById(Long id) { return Optional.ofNullable(db.get(id)); }

    public Template save(Template t) {
        if (t.getId() == null) t.setId(seq.getAndIncrement());
        db.put(t.getId(), t);
        return t;
    }
}