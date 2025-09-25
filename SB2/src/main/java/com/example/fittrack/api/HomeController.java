package com.example.fittrack.api;

import com.example.fittrack.service.ThirdPartyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ThirdPartyService thirdPartyService;

    public HomeController(ThirdPartyService thirdPartyService) {
        this.thirdPartyService = thirdPartyService;
    }

    @GetMapping("/")
    public String home(Model model) {
        String quote = thirdPartyService.fetchMotivationalQuote();
        model.addAttribute("quote", quote);
        return "index";
    }
}
