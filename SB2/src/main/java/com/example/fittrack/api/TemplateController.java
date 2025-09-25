package com.example.fittrack.api;

import com.example.fittrack.exception.NotFoundException;
import com.example.fittrack.model.Template;
import com.example.fittrack.repo.TemplateRepo;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateRepo repo;

    public TemplateController(TemplateRepo repo) {
        this.repo = repo;
    }

    @GetMapping
    public List<Template> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Template one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Template " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Template create(@Valid @RequestBody Template t) {
        return repo.save(t);
    }

    @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Template createFromForm(@Valid @ModelAttribute Template t) {
        return repo.save(t);
    }

}