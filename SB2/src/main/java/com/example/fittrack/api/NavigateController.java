package com.example.fittrack.api;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class NavigateController {

    @GetMapping("/_goto/workout")
    public String gotoWorkout(@RequestParam Long id) {
        return "redirect:/api/workouts/" + id;
    }

    @GetMapping("/_goto/advice")
    public String gotoAdvice(@RequestParam Long id) {
        return "redirect:/api/advice/" + id;
    }

    @GetMapping("/_goto/template")
    public String gotoTemplate(@RequestParam Long id) {
        return "redirect:/api/templates/" + id;
    }
}
