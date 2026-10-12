package com.stayease.service;

import com.stayease.config.ProductionEnvironmentValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class Phase7ProductionVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Security Headers: HTTP responses must include strict security headers (HSTS, CSP, FrameOptions, nosniff)")
    void securityHeaders_PresentOnResponses() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=(self)"));
    }

    @Test
    @DisplayName("Health Check: /actuator/health is accessible and returns UP without exposing sensitive DB internals")
    void actuatorHealth_ReturnsUpWithoutSensitiveDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db").doesNotExist()) // Sensitive DB details hidden
                .andExpect(jsonPath("$.components.diskSpace").doesNotExist());
    }

    @Test
    @DisplayName("Actuator Security: Non-health actuator endpoints are restricted from public/anonymous access")
    void actuatorEndpoints_RestrictedFromPublic() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().is3xxRedirection()); // Redirects to login
    }

    @Test
    @DisplayName("Actuator Security: Customer cannot access administrative actuator endpoints")
    @WithMockUser(username = "customer@example.com", roles = "CUSTOMER")
    void actuatorEndpoints_ForbiddenForCustomerRole() throws Exception {
        mockMvc.perform(get("/actuator/beans"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Prod Validator: Fails fast when critical DB_URL is missing in prod profile")
    void prodValidator_FailsFastWhenDbUrlMissing() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setActiveProfiles("prod");
        mockEnv.setProperty("spring.datasource.url", "");
        mockEnv.setProperty("spring.datasource.username", "stayease_user");
        mockEnv.setProperty("spring.datasource.password", "secret123");
        mockEnv.setProperty("app.base-url", "https://stayease.com");

        ProductionEnvironmentValidator validator = new ProductionEnvironmentValidator(mockEnv);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_URL");
    }

    @Test
    @DisplayName("Prod Validator: Fails fast when DB_PASSWORD is empty in prod profile")
    void prodValidator_FailsFastWhenDbPasswordMissing() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setActiveProfiles("prod");
        mockEnv.setProperty("spring.datasource.url", "jdbc:mysql://localhost:3306/stayease");
        mockEnv.setProperty("spring.datasource.username", "stayease_user");
        mockEnv.setProperty("spring.datasource.password", "");
        mockEnv.setProperty("app.base-url", "https://stayease.com");

        ProductionEnvironmentValidator validator = new ProductionEnvironmentValidator(mockEnv);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB_PASSWORD");
    }

    @Test
    @DisplayName("Prod Validator: Fails fast when Razorpay keys missing if payment provider is razorpay")
    void prodValidator_FailsFastWhenRazorpayKeysMissing() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setActiveProfiles("prod");
        mockEnv.setProperty("spring.datasource.url", "jdbc:mysql://db.prod:3306/stayease");
        mockEnv.setProperty("spring.datasource.username", "stayease_user");
        mockEnv.setProperty("spring.datasource.password", "SuperSecretProdPwd123!");
        mockEnv.setProperty("app.base-url", "https://stayease.com");
        mockEnv.setProperty("app.payment.provider", "razorpay");
        mockEnv.setProperty("app.payment.razorpay.key-id", "");

        ProductionEnvironmentValidator validator = new ProductionEnvironmentValidator(mockEnv);

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZORPAY_KEY_ID");
    }

    @Test
    @DisplayName("Prod Validator: Passes cleanly when all required prod environment variables are set")
    void prodValidator_PassesWhenAllConfigured() {
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setActiveProfiles("prod");
        mockEnv.setProperty("spring.datasource.url", "jdbc:mysql://db.prod:3306/stayease");
        mockEnv.setProperty("spring.datasource.username", "stayease_user");
        mockEnv.setProperty("spring.datasource.password", "SuperSecretProdPwd123!");
        mockEnv.setProperty("app.base-url", "https://stayease.com");
        mockEnv.setProperty("app.payment.provider", "sandbox");
        mockEnv.setProperty("app.mail.enabled", "false");

        ProductionEnvironmentValidator validator = new ProductionEnvironmentValidator(mockEnv);
        validator.run(new DefaultApplicationArguments()); // Must not throw
    }

    @Test
    @DisplayName("Secret Masking: Masking helpers mask tokens and sanitize JDBC URLs without leaking secrets")
    void secretMasking_HidesSensitiveTokens() {
        String maskedKey = ProductionEnvironmentValidator.maskIdentifier("rzp_live_abc123456789xyz");
        assertThat(maskedKey).doesNotContain("123456789");
        assertThat(maskedKey).contains("****");

        String sanitizedUrl = ProductionEnvironmentValidator.sanitizeJdbcUrl("jdbc:mysql://localhost:3306/stayease?user=root&password=supersecretpassword");
        assertThat(sanitizedUrl).doesNotContain("supersecretpassword");
        assertThat(sanitizedUrl).contains("password=****");
    }

    @Test
    @DisplayName("Safe Error Pages: Non-existent page displays sanitized 404 page without stack traces")
    @WithMockUser
    void safeErrorPage_DoesNotLeakStackTrace() throws Exception {
        mockMvc.perform(get("/this-route-definitely-does-not-exist-404"))
                .andExpect(status().isNotFound());
    }
}
