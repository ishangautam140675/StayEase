package com.stayease.controller;

import com.stayease.dto.AdminDashboardStats;
import com.stayease.model.User;
import com.stayease.repository.UserRepository;
import com.stayease.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * Administrative console controller (Phase 13).
 */
@Controller
public class AdminController {

    private final UserRepository userRepository;
    private final DashboardService dashboardService;

    public AdminController(UserRepository userRepository, DashboardService dashboardService) {
        this.userRepository = userRepository;
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(@org.springframework.web.bind.annotation.RequestParam(value = "startDate", required = false)
                            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
                            @org.springframework.web.bind.annotation.RequestParam(value = "endDate", required = false)
                            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
                            Principal principal, Model model) {
        String userName = principal != null ? principal.getName() : "Administrator";
        if (principal != null) {
            userName = userRepository.findByEmail(principal.getName())
                    .map(User::getFullName)
                    .orElse(principal.getName());
        }
        AdminDashboardStats stats = dashboardService.adminStats();
        com.stayease.dto.AdminAnalyticsReport report = dashboardService.generateAnalyticsReport(startDate, endDate);

        model.addAttribute("userName", userName);
        model.addAttribute("stats", stats);
        model.addAttribute("report", report);
        model.addAttribute("startDate", report.getStartDate());
        model.addAttribute("endDate", report.getEndDate());
        return "admin/dashboard";
    }
}
