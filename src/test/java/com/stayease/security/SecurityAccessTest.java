package com.stayease.security;

// Add every new route to this matrix in later phases.

import com.stayease.model.Role;
import com.stayease.model.User;
import com.stayease.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Add every new route to this matrix in later phases.
 *
 * Comprehensive authorization test matrix and session hardening verification
 * for Phase 6. Validates access permissions across all identities and routes,
 * CSRF token enforcement, session fixation protection, and post-logout isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SecurityAccessTest {

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
        userRepository.findByEmail("matrix.customer@stayease.com")
                .ifPresent(userRepository::delete);
        userRepository.findByEmail("matrix.admin@stayease.com")
                .ifPresent(userRepository::delete);

        testCustomer = new User(
                "Matrix Customer",
                "matrix.customer@stayease.com",
                passwordEncoder.encode("CustomerPass123"),
                "1234567890",
                Role.CUSTOMER
        );
        userRepository.save(testCustomer);

        testAdmin = new User(
                "Matrix Admin",
                "matrix.admin@stayease.com",
                passwordEncoder.encode("AdminPass123"),
                null,
                Role.ADMIN
        );
        userRepository.save(testAdmin);
    }

    // ── Authorization Matrix ───────────────────────────────────────────────

    enum ExpectedAccess {
        OK,             // 200 OK
        FORBIDDEN,      // 403 Forbidden
        REDIRECT_LOGIN  // 302 Redirect to /login
    }

    @ParameterizedTest(name = "[{index}] route={0}, identity={1} => expected={2}")
    @CsvSource({
            // Public routes — 200 for everyone
            "/,                   ANONYMOUS, OK",
            "/,                   CUSTOMER,  OK",
            "/,                   ADMIN,     OK",
            "/rooms,              ANONYMOUS, OK",
            "/rooms,              CUSTOMER,  OK",
            "/rooms,              ADMIN,     OK",
            "/register,           ANONYMOUS, OK",
            "/register,           CUSTOMER,  OK",
            "/register,           ADMIN,     OK",
            "/login,              ANONYMOUS, OK",
            "/login,              CUSTOMER,  OK",
            "/login,              ADMIN,     OK",

            // /customer/dashboard — anonymous redirects, CUSTOMER 200, ADMIN 403
            "/customer/dashboard,    ANONYMOUS, REDIRECT_LOGIN",
            "/customer/dashboard,    CUSTOMER,  OK",
            "/customer/dashboard,    ADMIN,     FORBIDDEN",

            // /customer/bookings — anonymous redirects, CUSTOMER 200, ADMIN 403
            "/customer/bookings,     ANONYMOUS, REDIRECT_LOGIN",
            "/customer/bookings,     CUSTOMER,  OK",
            "/customer/bookings,     ADMIN,     FORBIDDEN",

            // /customer/bookings/new — anonymous redirects, CUSTOMER 200, ADMIN 403
            "/customer/bookings/new, ANONYMOUS, REDIRECT_LOGIN",
            "/customer/bookings/new, CUSTOMER,  OK",
            "/customer/bookings/new, ADMIN,     FORBIDDEN",

            // /admin/dashboard — anonymous redirects, CUSTOMER 403, ADMIN 200
            "/admin/dashboard,    ANONYMOUS, REDIRECT_LOGIN",
            "/admin/dashboard,    CUSTOMER,  FORBIDDEN",
            "/admin/dashboard,    ADMIN,     OK",

            // /admin/rooms — anonymous redirects, CUSTOMER 403, ADMIN 200
            "/admin/rooms,        ANONYMOUS, REDIRECT_LOGIN",
            "/admin/rooms,        CUSTOMER,  FORBIDDEN",
            "/admin/rooms,        ADMIN,     OK",

            // /admin/rooms/new — anonymous redirects, CUSTOMER 403, ADMIN 200
            "/admin/rooms/new,    ANONYMOUS, REDIRECT_LOGIN",
            "/admin/rooms/new,    CUSTOMER,  FORBIDDEN",
            "/admin/rooms/new,    ADMIN,     OK",

            // /admin/bookings — anonymous redirects, CUSTOMER 403, ADMIN 200
            "/admin/bookings,     ANONYMOUS, REDIRECT_LOGIN",
            "/admin/bookings,     CUSTOMER,  FORBIDDEN",
            "/admin/bookings,     ADMIN,     OK"
    })
    @DisplayName("Authorization test matrix for all current routes and roles")
    void authorizationMatrix(String route, String identity, ExpectedAccess expected) throws Exception {
        MockHttpServletRequestBuilder requestBuilder = get(route);

        if ("CUSTOMER".equalsIgnoreCase(identity)) {
            requestBuilder.with(user("matrix.customer@stayease.com").roles("CUSTOMER"));
        } else if ("ADMIN".equalsIgnoreCase(identity)) {
            requestBuilder.with(user("matrix.admin@stayease.com").roles("ADMIN"));
        }
        // ANONYMOUS needs no user decoration

        ResultActions result = mockMvc.perform(requestBuilder);

        switch (expected) {
            case OK -> result.andExpect(status().isOk());
            case FORBIDDEN -> result.andExpect(status().isForbidden());
            case REDIRECT_LOGIN -> result
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    // ── Extra Security & Session Hardening Tests ───────────────────────────

    @Test
    @DisplayName("POST /logout without a CSRF token returns 403")
    void postLogout_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/logout"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /register without a CSRF token returns 403")
    void postRegister_withoutCsrf_returns403() throws Exception {
        mockMvc.perform(post("/register")
                        .param("fullName", "Attacker")
                        .param("email", "attacker@example.com")
                        .param("password", "HackedPass1")
                        .param("confirmPassword", "HackedPass1"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("The session id changes after a successful login (Session Fixation Protection)")
    void sessionId_changesAfterSuccessfulLogin() throws Exception {
        MockHttpSession preLoginSession = new MockHttpSession();
        String preLoginSessionId = preLoginSession.getId();

        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .session(preLoginSession)
                        .param("email", testCustomer.getEmail())
                        .param("password", "CustomerPass123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/dashboard"))
                .andReturn();

        HttpSession postLoginSession = result.getRequest().getSession(false);
        assertThat(postLoginSession).isNotNull();
        assertThat(postLoginSession.getId())
                .as("Session ID must change upon successful login to prevent session fixation")
                .isNotEqualTo(preLoginSessionId);
    }

    @Test
    @DisplayName("After logout, the previous session cannot open /customer/dashboard")
    void afterLogout_previousSession_cannotAccessCustomerDashboard() throws Exception {
        // 1. Establish an authenticated session via form login
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", testCustomer.getEmail())
                        .param("password", "CustomerPass123"))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession();
        assertThat(session).isNotNull();

        // 2. Active session successfully accesses /customer/dashboard
        mockMvc.perform(get("/customer/dashboard").session(session))
                .andExpect(status().isOk());

        // 3. Logout with the active session and valid CSRF
        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        // 4. Inactive/terminated session can no longer access /customer/dashboard
        mockMvc.perform(get("/customer/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
