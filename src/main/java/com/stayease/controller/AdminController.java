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
    public String dashboard(Principal principal, Model model) {
        String userName = principal != null ? principal.getName() : "Administrator";
        if (principal != null) {
            userName = userRepository.findByEmail(principal.getName())
                    .map(User::getFullName)
                    .orElse(principal.getName());
        }
        AdminDashboardStats stats = dashboardService.adminStats();

        model.addAttribute("userName", userName);
        model.addAttribute("stats", stats);
        return "admin/dashboard";
    }
}
