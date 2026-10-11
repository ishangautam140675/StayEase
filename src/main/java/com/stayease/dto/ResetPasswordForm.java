package com.stayease.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form for resetting password using an emailed token.
 */
public class ResetPasswordForm {

    @NotBlank(message = "Reset token is missing")
    private String token;

    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 100, message = "New password must be between 6 and 100 characters")
    private String newPassword;

    @NotBlank(message = "Please confirm your new password")
    private String confirmPassword;

    public ResetPasswordForm() {
    }

    public ResetPasswordForm(String token) {
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}
