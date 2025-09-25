package com.example.fittrack.api;

import com.example.fittrack.exception.BadRequestException;
import com.example.fittrack.exception.NotFoundException;
import com.example.fittrack.model.Workout;
import com.example.fittrack.repo.WorkoutRepo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/workouts")
public class WorkoutController {

    private final WorkoutRepo repo;

    public WorkoutController(WorkoutRepo repo) {
        this.repo = repo;
    }


    @GetMapping
    public List<Workout> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Workout one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Workout " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Workout create(@Valid @RequestBody Workout w) {
        if (w.getMinutes() == 0 && w.getReps() == 0) {
            throw new BadRequestException("minutes or reps must be > 0");
        }
        return repo.save(w);
    }

    @PutMapping("/{id}")
    public Workout update(@PathVariable Long id, @Valid @RequestBody Workout w) {
        if (!repo.existsById(id)) throw new NotFoundException("Workout " + id + " not found");
        if (w.getMinutes() == 0 && w.getReps() == 0) {
            throw new BadRequestException("minutes or reps must be > 0");
        }
        w.setId(id);
        return repo.save(w);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        if (!repo.existsById(id)) throw new NotFoundException("Workout " + id + " not found");
        repo.deleteById(id);
    }


    @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Workout createFromForm(@Valid @ModelAttribute Workout w) {
        if (w.getMinutes() == 0 && w.getReps() == 0) {
            throw new BadRequestException("minutes or reps must be > 0");
        }
        return repo.save(w);
    }

    @PostMapping(value = "/form/update", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Workout updateFromForm(@RequestParam Long id, @Valid @ModelAttribute Workout w) {
        if (!repo.existsById(id)) throw new NotFoundException("Workout " + id + " not found");
        if (w.getMinutes() == 0 && w.getReps() == 0) {
            throw new BadRequestException("minutes or reps must be > 0");
        }
        w.setId(id);
        return repo.save(w);
    }

    @PostMapping(value = "/form/delete", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> deleteFromForm(@RequestParam Long id) {
        if (!repo.existsById(id)) throw new NotFoundException("Workout " + id + " not found");
        repo.deleteById(id);
        return Map.of("deleted", id);
    }

}