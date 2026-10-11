package com.stayease.security;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Phase 1 — Authentication, Authorization, and Independent Sessions Verification Suite.
 *
 * Verifies:
 * 1. Simultaneous multi-user / multi-device sessions for customers and administrators.
 * 2. Complete logout isolation (logging out in session A has zero effect on session B).
 * 3. Strict role-based segregation (customers cannot access admin endpoints, admin cannot access customer endpoints).
 * 4. Session fixation protection (session ID changes upon successful authentication).
 * 5. Session expiration handling (invalidated session safely redirects to login).
 * 6. Session cookie cleanup on logout (both JSESSIONID and STAYEASE_SESSION).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Phase1IndependentSessionsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User customerAlice;
    private User customerBob;
    private User adminUser;

    @BeforeEach
    void setUp() {
        userRepository.findByEmail("phase1.alice@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("phase1.bob@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("phase1.admin@stayease.com").ifPresent(userRepository::delete);

        customerAlice = new User(
                "Alice Wonder",
                "phase1.alice@stayease.com",
                passwordEncoder.encode("AlicePass123!"),
                "9876543210",
                Role.CUSTOMER
        );
        userRepository.save(customerAlice);

        customerBob = new User(
                "Bob Builder",
                "phase1.bob@stayease.com",
                passwordEncoder.encode("BobPass123!"),
                "9876543211",
                Role.CUSTOMER
        );
        userRepository.save(customerBob);

        adminUser = new User(
                "Admin Phase1",
                "phase1.admin@stayease.com",
                passwordEncoder.encode("AdminPass123!"),
                "9876543212",
                Role.ADMIN
        );
        userRepository.save(adminUser);
    }

    private MockHttpSession loginUser(String email, String password, String expectedRedirect) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(expectedRedirect))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    @Test
    @DisplayName("Multiple customers can hold concurrent independent sessions; logout on one does not affect the other")
    void simultaneousCustomerSessions_independentLifecycleAndLogout() throws Exception {
        // 1. Log in Customer Alice in Session A (e.g. Device 1 / Browser 1)
        MockHttpSession sessionAlice = loginUser("phase1.alice@stayease.com", "AlicePass123!", "/customer/dashboard");

        // 2. Log in Customer Bob in Session B (e.g. Device 2 / Browser 2)
        MockHttpSession sessionBob = loginUser("phase1.bob@stayease.com", "BobPass123!", "/customer/dashboard");

        assertThat(sessionAlice.getId()).isNotEqualTo(sessionBob.getId());

        // 3. Both sessions access customer portal independently
        mockMvc.perform(get("/customer/dashboard").session(sessionAlice))
                .andExpect(status().isOk());

        mockMvc.perform(get("/customer/dashboard").session(sessionBob))
                .andExpect(status().isOk());

        // 4. Customer Alice logs out from Session A
        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(sessionAlice))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge("JSESSIONID", 0));

        // 5. Session A is invalidated and cannot access protected customer routes
        mockMvc.perform(get("/customer/dashboard").session(sessionAlice))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // 6. CRITICAL ISOLATION: Session B remains authenticated and fully operational
        mockMvc.perform(get("/customer/dashboard").session(sessionBob))
                .andExpect(status().isOk());

        mockMvc.perform(get("/customer/bookings").session(sessionBob))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin and Customer can operate concurrently; admin logout leaves customer session intact")
    void simultaneousAdminAndCustomer_independentLifecycleAndLogout() throws Exception {
        // 1. Admin logs in
        MockHttpSession sessionAdmin = loginUser("phase1.admin@stayease.com", "AdminPass123!", "/admin/dashboard");

        // 2. Customer logs in
        MockHttpSession sessionCustomer = loginUser("phase1.alice@stayease.com", "AlicePass123!", "/customer/dashboard");

        assertThat(sessionAdmin.getId()).isNotEqualTo(sessionCustomer.getId());

        // 3. Admin accesses admin portal; Customer accesses customer portal
        mockMvc.perform(get("/admin/dashboard").session(sessionAdmin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/customer/dashboard").session(sessionCustomer))
                .andExpect(status().isOk());

        // 4. Admin logs out
        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(sessionAdmin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        // 5. Admin session is invalidated
        mockMvc.perform(get("/admin/dashboard").session(sessionAdmin))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));

        // 6. Customer session remains active and completely untouched
        mockMvc.perform(get("/customer/dashboard").session(sessionCustomer))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Strict Role Boundary: Customer cannot access any admin operational endpoints")
    void customerCannotAccessAdminEndpoints() throws Exception {
        MockHttpSession customerSession = loginUser("phase1.alice@stayease.com", "AlicePass123!", "/customer/dashboard");

        String[] adminEndpoints = {
                "/admin/dashboard",
                "/admin/rooms",
                "/admin/rooms/new",
                "/admin/bookings",
                "/admin/operations/maintenance",
                "/admin/operations/pricing",
                "/admin/operations/audit-logs",
                "/actuator/beans",
                "/actuator/env"
        };

        for (String endpoint : adminEndpoints) {
            mockMvc.perform(get(endpoint).session(customerSession))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Strict Role Boundary: Admin cannot access customer-specific endpoints")
    void adminCannotAccessCustomerEndpoints() throws Exception {
        MockHttpSession adminSession = loginUser("phase1.admin@stayease.com", "AdminPass123!", "/admin/dashboard");

        String[] customerEndpoints = {
                "/customer/dashboard",
                "/customer/bookings",
                "/customer/profile",
                "/customer/favorites"
        };

        for (String endpoint : customerEndpoints) {
            mockMvc.perform(get(endpoint).session(adminSession))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("Session Fixation Protection: Session ID rotates upon login to prevent fixation hijacking")
    void sessionFixationProtection_rotatesSessionIdOnLogin() throws Exception {
        // Create an unauthenticated session (e.g. visiting home page first)
        MvcResult preLoginResult = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();

        MockHttpSession preSession = (MockHttpSession) preLoginResult.getRequest().getSession(true);
        String initialSessionId = preSession.getId();

        // Perform login within that session
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .with(csrf())
                        .session(preSession)
                        .param("email", "phase1.alice@stayease.com")
                        .param("password", "AlicePass123!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/customer/dashboard"))
                .andReturn();

        MockHttpSession postSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(postSession).isNotNull();
        assertThat(postSession.getId()).isNotEqualTo(initialSessionId);
    }

    @Test
    @DisplayName("Session Expiration: Expired/invalidated session cannot access protected data and redirects safely")
    void sessionExpiration_invalidatedSessionRedirectsToLogin() throws Exception {
        MockHttpSession session = loginUser("phase1.alice@stayease.com", "AlicePass123!", "/customer/dashboard");

        // Verify active access
        mockMvc.perform(get("/customer/dashboard").session(session))
                .andExpect(status().isOk());

        // Invalidate session (simulates 30m idle timeout or server-side expiry)
        session.invalidate();

        // Next request is rejected and redirected safely to login
        mockMvc.perform(get("/customer/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("Logout deletes both JSESSIONID and STAYEASE_SESSION cookies")
    void logoutDeletesBothCookieVariants() throws Exception {
        MockHttpSession session = loginUser("phase1.alice@stayease.com", "AlicePass123!", "/customer/dashboard");

        mockMvc.perform(post("/logout")
                        .with(csrf())
                        .session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().maxAge("STAYEASE_SESSION", 0));
    }
}
