package com.stayease.config;

import com.stayease.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration — Phase 5.
 *
 * <p>Public routes are open to everyone; /admin/** requires ADMIN role and
 * /customer/** requires CUSTOMER role; all other requests require any
 * authenticated user. CSRF stays enabled. Session-fixation protection
 * uses Spring Security's default (newSession).</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final RoleBasedSuccessHandler successHandler;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          RoleBasedSuccessHandler successHandler) {
        this.userDetailsService = userDetailsService;
        this.successHandler = successHandler;
    }

    /**
     * Password encoder bean — BCrypt, shared with {@link com.stayease.service.AuthService}
     * and {@link com.stayease.config.DataSeeder}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Wires the custom {@link CustomUserDetailsService} and {@link PasswordEncoder}
     * into Spring Security's DAO authentication provider.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    /**
     * The main HTTP security filter chain.
     *
     * <ul>
     *   <li>Public paths: {@code /}, {@code /rooms/**}, {@code /register},
     *       {@code /login}, static assets, and error pages.</li>
     *   <li>{@code /admin/**} — ADMIN only.</li>
     *   <li>{@code /customer/**} — CUSTOMER only.</li>
     *   <li>Everything else requires authentication.</li>
     *   <li>Form login with a custom success handler and failure URL.</li>
     *   <li>POST logout invalidates the session and deletes the cookie.</li>
     *   <li>CSRF enabled (default). Session-fixation uses default (newSession).</li>
     * </ul>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authenticationProvider(authenticationProvider())

            // ── CSRF (Exempt public inquiry & webhook endpoints that authenticate cryptographically) ─
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/discovery/inquire", "/api/webhooks/**", "/webhooks/**")
            )

            // ── Authorization ──────────────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                // Publicly accessible pages & webhook listeners
                .requestMatchers(
                    "/", "/rooms", "/rooms/**",
                    "/discovery", "/discovery/**", "/api/discovery", "/api/discovery/**",
                    "/register", "/login",
                    "/forgot-password", "/reset-password", "/verify-email",
                    "/api/webhooks/**", "/webhooks/**",
                    "/actuator/health", "/actuator/health/**", "/actuator/info",
                    "/css/**", "/js/**", "/images/**",
                    "/manifest.json", "/favicon.ico",
                    "/error", "/error/**"
                ).permitAll()
                // Role-restricted areas
                .requestMatchers("/actuator/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/customer/**").hasRole("CUSTOMER")
                // Everything else requires any authenticated user
                .anyRequest().authenticated()
            )

            // ── Security Headers (Production Hardening) ─────────────────────
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; font-src 'self' https://cdn.jsdelivr.net data:; img-src 'self' data: https:; connect-src 'self';")
                )
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(contentType -> {})
                .referrerPolicy(referrer -> referrer
                    .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                )
                .permissionsPolicy(permissions -> permissions
                    .policy("camera=(), microphone=(), geolocation=(self)")
                )
            )

            // ── Form Login ─────────────────────────────────────────────────
            .formLogin(form -> form
                .loginPage("/login")
                .usernameParameter("email")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureUrl("/login?error")
                .permitAll()
            )

            // ── Logout ─────────────────────────────────────────────────────
            .logout(logout -> logout
                .logoutUrl("/logout")                        // POST /logout
                .logoutSuccessUrl("/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "STAYEASE_SESSION")
                .permitAll()
            )

            // ── Session Fixation Protection ────────────────────────────────
            .sessionManagement(session -> session
                .sessionFixation(sf -> sf.changeSessionId())
            );

        return http.build();
    }
}
