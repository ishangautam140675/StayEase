package com.stayease.controller;

import com.stayease.model.User;
import com.stayease.service.AdminUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

/**
 * Administrative controller for viewing, updating, and removing user accounts.
 */
@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * Lists user directory with optional search query and pagination.
     */
    @GetMapping
    public String listUsers(@RequestParam(value = "query", required = false) String query,
                            @RequestParam(value = "page", defaultValue = "1") Integer page,
                            Model model) {
        int pageNum = (page != null && page > 0) ? page : 1;
        Pageable pageable = PageRequest.of(pageNum - 1, 15);
        Page<User> users = adminUserService.searchUsers(query, pageable);

        model.addAttribute("users", users.getContent());
        model.addAttribute("page", users);
        model.addAttribute("query", query);
        return "admin/users";
    }

    /**
     * Updates an existing user's information.
     */
    @PostMapping("/{id}/update")
    public String updateUser(@PathVariable("id") Long id,
                             @RequestParam("fullName") String fullName,
                             @RequestParam("email") String email,
                             @RequestParam(value = "phone", required = false) String phone,
                             @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                             RedirectAttributes redirectAttributes) {
        try {
            User updated = adminUserService.updateUser(id, fullName, email, phone);
            redirectAttributes.addFlashAttribute("successMessage",
                    "User profile for \"" + updated.getFullName() + "\" updated successfully.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to update user: " + e.getMessage());
        }

        if (redirectUrl != null && !redirectUrl.trim().isEmpty() && redirectUrl.startsWith("/")) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/admin/users";
    }

    /**
     * Removes a user and all associated records.
     */
    @PostMapping("/{id}/delete")
    public String removeUser(@PathVariable("id") Long id,
                             @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
                             Principal principal,
                             RedirectAttributes redirectAttributes) {
        try {
            String adminEmail = principal != null ? principal.getName() : "";
            adminUserService.removeUser(id, adminEmail);
            redirectAttributes.addFlashAttribute("successMessage", "User account removed successfully.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to remove user: " + e.getMessage());
        }

        if (redirectUrl != null && !redirectUrl.trim().isEmpty() && redirectUrl.startsWith("/")) {
            return "redirect:" + redirectUrl;
        }
        return "redirect:/admin/users";
    }
}
