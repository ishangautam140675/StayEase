package com.stayease.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handles navigation and page requests for StayEase.
 */
@Controller
public class HomeController {

    /**
     * GET / — renders the home / landing page.
     */
    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("activePage", "home");
        return "index";
    }
}
