package com.football.ua.service;

import com.football.ua.model.entity.Feedback;
import com.football.ua.model.FeedbackForm;
import com.football.ua.repo.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    public FeedbackService(FeedbackRepository feedbackRepository) {
        this.feedbackRepository = feedbackRepository;
    }

    public void addFeedback(FeedbackForm form, String usernameFromAuth) {
        String authorName = form.getName();
        if (authorName == null || authorName.isBlank()) {
            if (usernameFromAuth != null && !usernameFromAuth.isBlank()) {
                authorName = usernameFromAuth;
            } else {
                authorName = "гість";
            }
        }

        Feedback feedback = new Feedback();
        feedback.setAuthorName(authorName);
        feedback.setEmail(form.getEmail());
        feedback.setMessage(form.getMessage());
        feedback.setCreatedAt(LocalDateTime.now());

        feedbackRepository.save(feedback);
    }

    public List<Feedback> getAllFeedbacks() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc();
    }

    public void deleteFeedback(Long id) {
        feedbackRepository.deleteById(id);
    }

}
