package com.stayease.service;

import com.stayease.dto.RegisterForm;
import com.stayease.exception.EmailAlreadyUsedException;
import com.stayease.model.Role;
import com.stayease.model.User;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for {@link AuthService#register(RegisterForm)}.
 *
 * Uses the full Spring context with H2 so we exercise the real
 * repository, password encoder, and transactional boundary.
 */
@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    // ── helpers ──────────────────────────────────────────────────────────

    private RegisterForm validForm(String email) {
        RegisterForm f = new RegisterForm();
        f.setFullName("Jane Test");
        f.setEmail(email);
        f.setPhone("9876543210");
        f.setPassword("Secret99");
        f.setConfirmPassword("Secret99");
        return f;
    }

    // ── tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("register() creates a user with role CUSTOMER")
    void register_createsUserWithRoleCustomer() {
        RegisterForm form = validForm("jane@example.com");
        User saved = authService.register(form);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getEmail()).isEqualTo("jane@example.com");
        assertThat(saved.getFullName()).isEqualTo("Jane Test");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("register() stores a BCrypt hash, not the raw password")
    void register_storesBcryptHash() {
        String rawPassword = "Secret99";
        RegisterForm form = validForm("bcrypt@example.com");
        form.setPassword(rawPassword);
        form.setConfirmPassword(rawPassword);

        User saved = authService.register(form);
        String storedHash = saved.getPasswordHash();

        // Hash must NOT equal the raw password
        assertThat(storedHash).isNotEqualTo(rawPassword);

        // Hash must start with BCrypt prefix $2a$ or $2b$
        assertThat(storedHash).matches("\\$2[ab]\\$.+");

        // Encoder must verify the raw password against the stored hash
        assertThat(passwordEncoder.matches(rawPassword, storedHash)).isTrue();
    }

    @Test
    @DisplayName("register() with the same email (same case) throws EmailAlreadyUsedException")
    void register_sameEmail_throwsException() {
        authService.register(validForm("dup@example.com"));

        assertThrows(EmailAlreadyUsedException.class,
                () -> authService.register(validForm("dup@example.com")));
    }

    @Test
    @DisplayName("register() with same email in different letter case throws EmailAlreadyUsedException")
    void register_sameEmailDifferentCase_throwsException() {
        authService.register(validForm("alice@example.com"));

        RegisterForm upperForm = validForm("ALICE@EXAMPLE.COM");
        assertThrows(EmailAlreadyUsedException.class,
                () -> authService.register(upperForm));
    }

    @Test
    @DisplayName("register() trims and lower-cases the email before persisting")
    void register_normalizesEmail() {
        RegisterForm form = validForm("  Bob@Example.COM  ");
        User saved = authService.register(form);

        assertThat(saved.getEmail()).isEqualTo("bob@example.com");
    }

    @Test
    @DisplayName("register() with blank phone stores null")
    void register_blankPhone_storesNull() {
        RegisterForm form = validForm("nophone@example.com");
        form.setPhone("   ");
        User saved = authService.register(form);

        assertThat(saved.getPhone()).isNull();
    }
}
