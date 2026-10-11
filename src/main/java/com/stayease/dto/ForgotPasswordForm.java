package com.stayease.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Form for requesting account password recovery.
 */
public class ForgotPasswordForm {

    @NotBlank(message = "Email address is required")
    @Email(message = "Please enter a valid email address")
    private String email;

    public ForgotPasswordForm() {
    }

    public ForgotPasswordForm(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
