package com.stayease.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration.
 *
 * TODO: Replace this open permit-all rule in Phase 5 when form-login,
 *       role-based access control, and CSRF protection are implemented.
 *
 * For now every request is allowed so the landing page is reachable without
 * any credentials while we scaffold the application.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Defines the HTTP security filter chain.
     * In Phase 1 all requests are permitted so we can verify the
     * application starts and the home page renders correctly.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Allow every request without authentication (Phase 1 only)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );

        return http.build();
    }
}
