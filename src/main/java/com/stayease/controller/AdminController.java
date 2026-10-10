package com.stayease.controller;

import com.stayease.model.User;
import com.stayease.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

/**
 * Admin area controller — Phase 5 placeholder.
 * Full admin features will be built in Phase 13.
 */
@Controller
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/admin/dashboard")
    public String dashboard(Principal principal, Model model) {
        String userName = principal != null ? principal.getName() : "Administrator";
        if (principal != null) {
            userName = userRepository.findByEmail(principal.getName())
                    .map(User::getFullName)
                    .orElse(principal.getName());
        }
        model.addAttribute("userName", userName);
        return "admin/dashboard";
    }
}
