package com.stayease.service;

import com.stayease.dto.PasswordChangeForm;
import com.stayease.dto.ProfileUpdateForm;
import com.stayease.exception.InvalidBookingStateException;
import com.stayease.model.EmailVerificationToken;
import com.stayease.model.PasswordResetToken;
import com.stayease.model.Room;
import com.stayease.model.User;
import com.stayease.repository.EmailVerificationTokenRepository;
import com.stayease.repository.PasswordResetTokenRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;

/**
 * Service managing customer profile details, password recovery,
 * email verification, and favorite rooms.
 */
@Service
public class CustomerAccountService {

    private static final Logger log = LoggerFactory.getLogger(CustomerAccountService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public CustomerAccountService(UserRepository userRepository,
                                  RoomRepository roomRepository,
                                  PasswordResetTokenRepository passwordResetTokenRepository,
                                  EmailVerificationTokenRepository emailVerificationTokenRepository,
                                  PasswordEncoder passwordEncoder,
                                  EmailService emailService) {
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
    }

    /**
     * Updates customer name and contact details.
     */
    @Transactional
    public User updateProfile(String userEmail, ProfileUpdateForm form) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));

        user.setFullName(form.getFullName().trim());
        if (form.getPhone() != null) {
            user.setPhone(form.getPhone().trim());
        }
        return userRepository.save(user);
    }

    /**
     * Changes password for an authenticated user after verifying current password.
     */
    @Transactional
    public void changePassword(String userEmail, PasswordChangeForm form) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));

        if (!passwordEncoder.matches(form.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("The current password you entered is incorrect.");
        }

        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new IllegalArgumentException("New password and confirmation do not match.");
        }

        user.setPasswordHash(passwordEncoder.encode(form.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user {}", userEmail);
    }

    /**
     * Initiates password reset for a given email address.
     * Prevents account enumeration by always returning silently without throwing errors if email is not found.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        if (email == null || email.trim().isEmpty()) {
            return;
        }

        Optional<User> userOpt = userRepository.findByEmail(email.trim().toLowerCase());
        if (userOpt.isEmpty()) {
            log.info("Password reset requested for non-existent email. Processed silently to prevent enumeration.");
            return;
        }

        User user = userOpt.get();
        // Invalidate any existing unused tokens for this user
        passwordResetTokenRepository.deleteByUser(user);

        String token = generateSecureToken();
        PasswordResetToken resetToken = new PasswordResetToken(token, user, 30); // 30 minutes expiry
        passwordResetTokenRepository.save(resetToken);

        emailService.sendPasswordResetEmail(user, token);
        log.info("Password reset token generated and sent for user {}", user.getEmail());
    }

    /**
     * Validates and executes a password reset with a single-use token.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired password reset link."));

        if (!resetToken.isValid()) {
            throw new IllegalArgumentException("This password reset link has already been used or has expired.");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        log.info("Password successfully reset for user {}", user.getEmail());
    }

    /**
     * Validates whether a password reset token exists and is valid.
     */
    public boolean isPasswordResetTokenValid(String token) {
        if (token == null || token.trim().isEmpty()) return false;
        return passwordResetTokenRepository.findByToken(token)
                .map(PasswordResetToken::isValid)
                .orElse(false);
    }

    /**
     * Creates and sends an email verification link.
     */
    @Transactional
    public void requestEmailVerification(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));

        if (user.isEmailVerified()) {
            return;
        }

        emailVerificationTokenRepository.deleteByUser(user);

        String token = generateSecureToken();
        EmailVerificationToken verifyToken = new EmailVerificationToken(token, user, 24); // 24 hours expiry
        emailVerificationTokenRepository.save(verifyToken);

        emailService.sendEmailVerification(user, token);
        log.info("Email verification link generated and sent to {}", user.getEmail());
    }

    /**
     * Verifies an email address using a single-use verification token.
     */
    @Transactional
    public boolean verifyEmail(String token) {
        if (token == null || token.trim().isEmpty()) return false;

        Optional<EmailVerificationToken> tokenOpt = emailVerificationTokenRepository.findByToken(token);
        if (tokenOpt.isEmpty() || !tokenOpt.get().isValid()) {
            return false;
        }

        EmailVerificationToken verifyToken = tokenOpt.get();
        User user = verifyToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        verifyToken.setUsed(true);
        emailVerificationTokenRepository.save(verifyToken);
        log.info("Email successfully verified for user {}", user.getEmail());
        return true;
    }

    /**
     * Toggles a room as saved / favorite for the customer.
     */
    @Transactional
    public boolean toggleFavoriteRoom(String userEmail, Long roomId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Room not found with ID: " + roomId));

        boolean nowFavorite;
        if (user.hasFavorite(roomId)) {
            user.removeFavorite(room);
            nowFavorite = false;
        } else {
            user.addFavorite(room);
            nowFavorite = true;
        }
        userRepository.save(user);
        return nowFavorite;
    }

    /**
     * Gets all saved favorite rooms for a customer.
     */
    @Transactional(readOnly = true)
    public Set<Room> getFavoriteRooms(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        // Force initialization within transactional boundary
        user.getFavoriteRooms().size();
        return user.getFavoriteRooms();
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
