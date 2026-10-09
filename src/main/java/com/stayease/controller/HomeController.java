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

    /**
     * GET /rooms — renders the accommodations and room listing page.
     */
    @GetMapping("/rooms")
    public String rooms(Model model) {
        model.addAttribute("activePage", "rooms");
        return "rooms";
    }

    /**
     * GET /customer/dashboard — renders the Customer Dashboard.
     */
    @GetMapping("/customer/dashboard")
    public String customerDashboard(Model model) {
        model.addAttribute("activePage", "customer");
        return "customer/dashboard";
    }

    /**
     * GET /admin/dashboard — renders the Administrator Portal & Dashboard.
     */
    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        model.addAttribute("activePage", "admin");
        return "admin/dashboard";
    }
}
