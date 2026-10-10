package com.stayease.controller;

import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * MockMvc integration tests for {@link AuthController}.
 *
 * All POST requests include {@code .with(csrf())} because CSRF is enabled
 * by default in Spring Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    // ── GET /login ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /login returns 200 and shows the login page")
    void getLogin_returns200() throws Exception {
        mockMvc.perform(get("/login"))
               .andExpect(status().isOk())
               .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome back")));
    }

    // ── GET /register ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /register returns 200 and shows the registration form")
    void getRegister_returns200() throws Exception {
        mockMvc.perform(get("/register"))
               .andExpect(status().isOk())
               .andExpect(content().string(org.hamcrest.Matchers.containsString("Create your account")));
    }

    // ── POST /register — happy path ────────────────────────────────────────

    @Test
    @DisplayName("Valid POST /register redirects to /login?registered")
    void postRegister_validData_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Alice Smith")
                .param("email", "alice@example.com")
                .param("phone", "9876543210")
                .param("password", "Secure12")
                .param("confirmPassword", "Secure12"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login?registered"));

        assertThat(userRepository.existsByEmail("alice@example.com")).isTrue();
    }

    // ── POST /register — validation failures ──────────────────────────────

    @Test
    @DisplayName("POST /register with invalid email returns 200 and shows error; no user saved")
    void postRegister_invalidEmail_returns200WithError() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Bob Jones")
                .param("email", "not-an-email")
                .param("password", "Secure12")
                .param("confirmPassword", "Secure12"))
               .andExpect(status().isOk())
               .andExpect(content().string(org.hamcrest.Matchers.containsString("valid email")));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("POST /register with too-short password returns 200 and shows error; no user saved")
    void postRegister_shortPassword_returns200WithError() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Carol White")
                .param("email", "carol@example.com")
                .param("password", "Ab1")
                .param("confirmPassword", "Ab1"))
               .andExpect(status().isOk())
               .andExpect(content().string(org.hamcrest.Matchers.containsString("8 and 64")));

        assertThat(userRepository.existsByEmail("carol@example.com")).isFalse();
    }

    @Test
    @DisplayName("POST /register with password that has no digit returns 200 and shows error")
    void postRegister_passwordWithoutDigit_returns200WithError() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Dan Brown")
                .param("email", "dan@example.com")
                .param("password", "NoDigitPassword")
                .param("confirmPassword", "NoDigitPassword"))
               .andExpect(status().isOk())
               .andExpect(content().string(
                       org.hamcrest.Matchers.containsString("one letter and one digit")));

        assertThat(userRepository.existsByEmail("dan@example.com")).isFalse();
    }

    @Test
    @DisplayName("POST /register with mismatched passwords returns 200 and shows error; no user saved")
    void postRegister_mismatchedPasswords_returns200WithError() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Eve Black")
                .param("email", "eve@example.com")
                .param("password", "Secure12")
                .param("confirmPassword", "Different9"))
               .andExpect(status().isOk())
               .andExpect(content().string(
                       org.hamcrest.Matchers.containsString("Passwords do not match")));

        assertThat(userRepository.existsByEmail("eve@example.com")).isFalse();
    }

    @Test
    @DisplayName("POST /register with duplicate email returns 200 and shows error; still only one user")
    void postRegister_duplicateEmail_returns200WithError() throws Exception {
        // First registration
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Frank Lane")
                .param("email", "frank@example.com")
                .param("password", "Secure12")
                .param("confirmPassword", "Secure12"))
               .andExpect(status().is3xxRedirection());

        // Second registration with same email
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Frank Lane 2")
                .param("email", "frank@example.com")
                .param("password", "Secure12")
                .param("confirmPassword", "Secure12"))
               .andExpect(status().isOk())
               .andExpect(content().string(
                       org.hamcrest.Matchers.containsString("already exists")));

        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals("frank@example.com"))
                .count())
               .isEqualTo(1);
    }

    @Test
    @DisplayName("POST /register with role=ADMIN param still creates a CUSTOMER user")
    void postRegister_withRoleAdminParam_createsCustomer() throws Exception {
        mockMvc.perform(post("/register")
                .with(csrf())
                .param("fullName", "Grace Hall")
                .param("email", "grace@example.com")
                .param("password", "Secure12")
                .param("confirmPassword", "Secure12")
                .param("role", "ADMIN"))            // malicious extra param
               .andExpect(status().is3xxRedirection());

        userRepository.findByEmail("grace@example.com").ifPresent(user ->
                assertThat(user.getRole())
                        .as("Role must be CUSTOMER, even when role=ADMIN is sent in the request")
                        .isEqualTo(com.stayease.model.Role.CUSTOMER)
        );
    }
}
