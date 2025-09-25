package com.example.fittrack.repo;

import com.example.fittrack.model.Advice;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AdviceRepo {
    private final Map<Long, Advice> db = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    public List<Advice> findAll() { return new ArrayList<>(db.values()); }

    public Optional<Advice> findById(Long id) { return Optional.ofNullable(db.get(id)); }

    public Advice save(Advice a) {
        if (a.getId() == null) a.setId(seq.getAndIncrement());
        db.put(a.getId(), a);
        return a;
    }
}

