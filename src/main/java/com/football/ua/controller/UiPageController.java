package com.football.ua.controller;

import com.football.ua.model.FeedbackForm;
import com.football.ua.service.FeedbackService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
@RequestMapping("/ui")
public class UiPageController {

    private final FeedbackService feedbackService;

    public UiPageController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    @GetMapping("/home")
    public String home(Model model, Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "гість";
        model.addAttribute("username", username);
        model.addAttribute("feedbackList", feedbackService.getAllFeedbacks());
        return "ui-home";
    }

    @GetMapping("/feedback")
    public String feedbackForm(Model model) {
        model.addAttribute("feedbackForm", new FeedbackForm());
        return "ui-feedback";
    }

    @PostMapping("/feedback")
    public String handleFeedback(
            @ModelAttribute("feedbackForm") FeedbackForm form,
            Authentication authentication,
            Model model
    ) {
        String username = authentication != null ? authentication.getName() : null;
        feedbackService.addFeedback(form, username);

        model.addAttribute("feedbackForm", new FeedbackForm());
        model.addAttribute("successMessage", "Дякуємо за відгук про Football UA");

        return "ui-feedback";
    }

    @GetMapping("/roles")
    public String rolesDemo() {
        return "ui-roles";
    }

    @PostMapping("/feedback/delete/{id}")
    public String deleteFeedback(@PathVariable Long id) {
        feedbackService.deleteFeedback(id);
        return "redirect:/ui/home";
    }

}
