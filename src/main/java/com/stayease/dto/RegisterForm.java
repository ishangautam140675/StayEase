package com.stayease.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO for the user registration form.
 *
 * <p>Role is intentionally absent — it must never come from the form.
 * {@link com.stayease.service.AuthService} always assigns {@code CUSTOMER}.</p>
 */
public class RegisterForm {

    /** Full display name of the registering user. */
    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    /** Email address used as the login identifier (stored lower-cased). */
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 150, message = "Email must be at most 150 characters")
    private String email;

    /**
     * Optional mobile phone number.
     * Accepts an empty / blank value OR exactly 10 digits.
     */
    @Pattern(
        regexp = "^$|^[0-9]{10}$",
        message = "Phone number must be exactly 10 digits (or leave blank)"
    )
    private String phone;

    /**
     * Plain-text password chosen by the user.
     * Must be 8–64 characters and contain at least one letter and one digit.
     */
    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$",
        message = "Password must contain at least one letter and one digit"
    )
    private String password;

    /** Confirmation field — must equal {@code password} (checked in the controller). */
    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    // ── Getters & Setters ──────────────────────────────────────────────────

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
