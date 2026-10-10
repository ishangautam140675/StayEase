package com.stayease.config;

import com.stayease.model.Role;
import com.stayease.model.User;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for Spring Security — Phase 5:
 * - Route protection (/admin/**, /customer/**, public routes)
 * - Role-based authorization and 403 checks
 * - Form login authentication and role-based redirects
 * - Form logout, session invalidation and cookie deletion
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testCustomer;
    private User testAdmin;

    @BeforeEach
    void setUp() {
        testCustomer = new User(
                "Customer Jane",
                "customer@stayease.com",
                passwordEncoder.encode("CustomerPass123"),
                "1234567890",
                Role.CUSTOMER
        );
        userRepository.save(testCustomer);

        testAdmin = new User(
                "Admin John",
                "admin@stayease.com",
                passwordEncoder.encode("AdminPass123"),
                null,
                Role.ADMIN
        );
        userRepository.save(testAdmin);
    }

    // ── Anonymous access to protected routes ───────────────────────────────

    @Test
    @DisplayName("Anonymous GET /customer/dashboard redirects to /login")
    void anonymous_customerDashboard_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Anonymous GET /admin/dashboard redirects to /login")
    void anonymous_adminDashboard_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ── Role-based 403 Forbidden checks ────────────────────────────────────

    @Test
    @WithMockUser(roles = "CUSTOMER")
    @DisplayName("@WithMockUser(roles='CUSTOMER') GET /admin/dashboard returns 403")
    void customer_accessingAdminDashboard_returns403() throws Exception {
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("@WithMockUser(roles='ADMIN') GET /customer/dashboard returns 403")
    void admin_accessingCustomerDashboard_returns403() throws Exception {
        mockMvc.perform(get("/customer/dashboard"))
                .andExpect(status().isForbidden());
    }

    // ── Login with credentials & RoleBasedSuccessHandler ───────────────────

    @Test
    @DisplayName("POST /login with customer credentials redirects to /customer/dashboard")
    void postLogin_customerCredentials_redirectsToCustomerDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "customer@stayease.com")
                        .param("password", "CustomerPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/dashboard"));
    }

    @Test
    @DisplayName("POST /login with admin credentials redirects to /admin/dashboard")
    void postLogin_adminCredentials_redirectsToAdminDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "admin@stayease.com")
                        .param("password", "AdminPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/dashboard"));
    }

    @Test
    @DisplayName("POST /login with wrong password redirects to /login?error")
    void postLogin_wrongPassword_redirectsToLoginError() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "customer@stayease.com")
                        .param("password", "WrongPassword999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    // ── Logout & Session Invalidation ──────────────────────────────────────

    @Test
    @DisplayName("POST /logout redirects to /login?logout and old session cannot access /customer/dashboard")
    void postLogout_redirectsAndInvalidatesSession() throws Exception {
        // 1. Log in to establish an authenticated session
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "customer@stayease.com")
                        .param("password", "CustomerPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/dashboard"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();
        org.junit.jupiter.api.Assertions.assertNotNull(session);

        // 2. Verify customer can access dashboard with active session
        mockMvc.perform(get("/customer/dashboard").session(session))
                .andExpect(status().isOk());

        // 3. Perform POST /logout with the active session and csrf
        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        // 4. Old session can no longer open /customer/dashboard (redirects to /login)
        mockMvc.perform(get("/customer/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
