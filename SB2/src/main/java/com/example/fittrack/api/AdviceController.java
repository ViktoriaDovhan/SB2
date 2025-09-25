package com.example.fittrack.api;

import com.example.fittrack.exception.BadRequestException;
import com.example.fittrack.exception.NotFoundException;
import com.example.fittrack.model.Advice;
import com.example.fittrack.repo.AdviceRepo;
import com.example.fittrack.service.ThirdPartyService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/advice")
public class AdviceController {

    private final AdviceRepo repo;
    private final ThirdPartyService thirdPartyService;

    public AdviceController(AdviceRepo repo, ThirdPartyService thirdPartyService) {
        this.repo = repo;
        this.thirdPartyService = thirdPartyService;
    }

    @GetMapping
    public List<Advice> all() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public Advice one(@PathVariable Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Advice " + id + " not found"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Advice create(@Valid @RequestBody Advice a) {
        if (a.getText() == null || a.getText().trim().isEmpty()) {
            String quote = thirdPartyService.fetchMotivationalQuote();
            System.out.println("API quote: " + quote);
            if (quote != null && !quote.isEmpty()) {
                a.setText(quote);
            } else {
                throw new BadRequestException("Advice text empty and external API failed");
            }
        }
        return repo.save(a);
    }

    @PostMapping(value = "/form", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Advice createFromForm(@Valid @ModelAttribute Advice a) {
        return repo.save(a);
    }

}
