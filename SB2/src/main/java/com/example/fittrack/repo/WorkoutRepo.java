package com.example.fittrack.repo;

import com.example.fittrack.model.Workout;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class WorkoutRepo {
    private final Map<Long, Workout> db = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    public List<Workout> findAll() { return new ArrayList<>(db.values()); }

    public Optional<Workout> findById(Long id) { return Optional.ofNullable(db.get(id)); }

    public Workout save(Workout w) {
        if (w.getId() == null) w.setId(seq.getAndIncrement());
        db.put(w.getId(), w);
        return w;
    }

    public boolean existsById(Long id) { return db.containsKey(id); }

    public void deleteById(Long id) { db.remove(id); }
}