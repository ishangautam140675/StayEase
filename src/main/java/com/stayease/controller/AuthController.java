package com.stayease.controller;

import com.stayease.dto.RegisterForm;
import com.stayease.exception.EmailAlreadyUsedException;
import com.stayease.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles user-facing authentication pages: registration (Phase 4).
 * Login is handled by Spring Security in Phase 5.
 */
@Controller
public class AuthController {

    private final AuthService authService;
    private final com.stayease.service.CustomerAccountService customerAccountService;

    public AuthController(AuthService authService, com.stayease.service.CustomerAccountService customerAccountService) {
        this.authService = authService;
        this.customerAccountService = customerAccountService;
    }

    // ── GET /login ─────────────────────────────────────────────────────────

    /**
     * Renders the login page.
     */
    @GetMapping("/login")
    public String showLoginForm() {
        return "auth/login";
    }

    // ── GET /register ──────────────────────────────────────────────────────

    /**
     * Renders the registration form with a fresh, empty {@link RegisterForm}.
     */
    @GetMapping("/register")
    public String showRegisterForm(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "auth/register";
    }

    // ── POST /register ─────────────────────────────────────────────────────

    /**
     * Processes the registration form submission.
     *
     * <p>Validation order:
     * <ol>
     *   <li>Bean-validation errors ({@code @Valid}) — re-render on failure.</li>
     *   <li>Password / confirmPassword mismatch — adds a field error, re-renders.</li>
     *   <li>Duplicate email ({@link EmailAlreadyUsedException}) — adds a field
     *       error on {@code email}, re-renders.</li>
     *   <li>Success — redirect to {@code /login?registered} with a flash message.</li>
     * </ol>
     *
     * <p>Passwords are never placed back in the model; the form object returned
     * to the view has its password fields cleared automatically because Thymeleaf
     * does not re-populate {@code type="password"} inputs from the model.</p>
     */
    @PostMapping("/register")
    public String processRegister(
            @Valid RegisterForm registerForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        // 1. Password-match check (done before calling the service)
        if (!bindingResult.hasFieldErrors("password")
                && !bindingResult.hasFieldErrors("confirmPassword")
                && registerForm.getPassword() != null
                && !registerForm.getPassword().equals(registerForm.getConfirmPassword())) {

            bindingResult.rejectValue(
                    "confirmPassword",
                    "registerForm.confirmPassword.mismatch",
                    "Passwords do not match");
        }

        // 2. Re-render on any validation error
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        // 3. Attempt registration — catch duplicate-email business error
        try {
            authService.register(registerForm);
        } catch (EmailAlreadyUsedException ex) {
            bindingResult.rejectValue(
                    "email",
                    "registerForm.email.exists",
                    "An account with this email already exists");
            return "auth/register";
        }

        // 4. Success
        redirectAttributes.addFlashAttribute("successMessage",
                "Account created. Please log in.");
        return "redirect:/login?registered";
    }

    // ── GET & POST /forgot-password ────────────────────────────────────────

    @GetMapping("/forgot-password")
    public String showForgotPasswordForm(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new com.stayease.dto.ForgotPasswordForm());
        }
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(
            @Valid com.stayease.dto.ForgotPasswordForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }

        // Safe execution: always show neutral success notice to avoid account enumeration
        customerAccountService.requestPasswordReset(form.getEmail());

        redirectAttributes.addFlashAttribute("infoMessage",
                "If an account with that email address exists in our system, we have sent instructions to reset your password.");
        return "redirect:/login";
    }

    // ── GET & POST /reset-password ─────────────────────────────────────────

    @GetMapping("/reset-password")
    public String showResetPasswordForm(
            @org.springframework.web.bind.annotation.RequestParam(value = "token", required = false) String token,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (token == null || !customerAccountService.isPasswordResetTokenValid(token)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This password reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        if (!model.containsAttribute("resetPasswordForm")) {
            model.addAttribute("resetPasswordForm", new com.stayease.dto.ResetPasswordForm(token));
        }
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(
            @Valid com.stayease.dto.ResetPasswordForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (!customerAccountService.isPasswordResetTokenValid(form.getToken())) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This password reset link is invalid or has expired. Please request a new one.");
            return "redirect:/forgot-password";
        }

        if (!bindingResult.hasFieldErrors("newPassword")
                && !bindingResult.hasFieldErrors("confirmPassword")
                && form.getNewPassword() != null
                && !form.getNewPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "resetPasswordForm.confirmPassword.mismatch", "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }

        try {
            customerAccountService.resetPassword(form.getToken(), form.getNewPassword());
            redirectAttributes.addFlashAttribute("successMessage",
                    "Your password has been successfully reset. Please log in with your new password.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/forgot-password";
        }
    }

    // ── GET /verify-email ──────────────────────────────────────────────────

    @GetMapping("/verify-email")
    public String verifyEmail(
            @org.springframework.web.bind.annotation.RequestParam(value = "token", required = false) String token,
            RedirectAttributes redirectAttributes) {

        if (token == null || !customerAccountService.verifyEmail(token)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "This email verification link is invalid or has expired.");
            return "redirect:/login";
        }

        redirectAttributes.addFlashAttribute("successMessage",
                "Your email address has been successfully verified! Thank you.");
        return "redirect:/login";
    }
}

