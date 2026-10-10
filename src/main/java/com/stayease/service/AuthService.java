package com.stayease.service;

import com.stayease.dto.RegisterForm;
import com.stayease.exception.EmailAlreadyUsedException;
import com.stayease.model.Role;
import com.stayease.model.User;
import com.stayease.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service handling user authentication operations such as registration.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user from the given registration form.
     *
     * <p>The email is trimmed and lower-cased before any lookup or persistence,
     * so {@code Alice@Example.com} and {@code alice@example.com} are treated as
     * the same address. The role is always set to {@link Role#CUSTOMER} —
     * it is never read from the form.</p>
     *
     * @param form the validated registration form (passwords already confirmed
     *             equal by the controller before calling this method)
     * @return the newly persisted {@link User}
     * @throws EmailAlreadyUsedException if the email is already registered
     */
    @Transactional
    public User register(RegisterForm form) {
        String normalizedEmail = form.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyUsedException(normalizedEmail);
        }

        String phone = (form.getPhone() == null || form.getPhone().isBlank())
                ? null
                : form.getPhone().trim();

        User user = new User(
                form.getFullName().trim(),
                normalizedEmail,
                passwordEncoder.encode(form.getPassword()),
                phone,
                Role.CUSTOMER          // role is NEVER taken from the form
        );

        return userRepository.save(user);
    }
}
