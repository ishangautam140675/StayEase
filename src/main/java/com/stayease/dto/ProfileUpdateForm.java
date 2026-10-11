package com.stayease.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Form backing customer profile edits (name, phone).
 */
public class ProfileUpdateForm {

    @NotBlank(message = "Full name cannot be blank")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @Size(max = 20, message = "Phone number cannot exceed 20 characters")
    @Pattern(regexp = "^$|^[+0-9\\-\\s()]{7,20}$", message = "Please enter a valid phone number")
    private String phone;

    public ProfileUpdateForm() {
    }

    public ProfileUpdateForm(String fullName, String phone) {
        this.fullName = fullName;
        this.phone = phone;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
