package com.stayease.service;

import com.stayease.model.Booking;
import com.stayease.model.Role;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.BookingStatusHistoryRepository;
import com.stayease.repository.EmailVerificationTokenRepository;
import com.stayease.repository.PasswordResetTokenRepository;
import com.stayease.repository.PaymentTransactionRepository;
import com.stayease.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service managing user directory, updates, and removals for administrative control.
 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingStatusHistoryRepository bookingStatusHistoryRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;

    public AdminUserService(UserRepository userRepository,
                            BookingRepository bookingRepository,
                            BookingStatusHistoryRepository bookingStatusHistoryRepository,
                            PaymentTransactionRepository paymentTransactionRepository,
                            PasswordResetTokenRepository passwordResetTokenRepository,
                            EmailVerificationTokenRepository emailVerificationTokenRepository) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingStatusHistoryRepository = bookingStatusHistoryRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
    }

    @Transactional(readOnly = true)
    public Page<User> searchUsers(String query, Pageable pageable) {
        return userRepository.searchUsers(query, pageable);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Transactional
    public User updateUser(Long id, String fullName, String email, String phone) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        String normalizedEmail = email != null ? email.trim().toLowerCase() : "";
        if (normalizedEmail.isEmpty()) {
            throw new IllegalArgumentException("Email address cannot be empty.");
        }

        // Check if new email is already registered to a different user
        Optional<User> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent() && !existing.get().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Email '" + normalizedEmail + "' is already registered to another user.");
        }

        if (fullName == null || fullName.trim().isEmpty()) {
            throw new IllegalArgumentException("Full name cannot be empty.");
        }

        user.setFullName(fullName.trim());
        user.setEmail(normalizedEmail);
        if (phone != null) {
            user.setPhone(phone.trim());
        }

        User saved = userRepository.save(user);
        log.info("Admin updated user id={} (name={}, email={})", id, saved.getFullName(), saved.getEmail());
        return saved;
    }

    @Transactional
    public void removeUser(Long id, String currentAdminEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));

        if (user.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            throw new IllegalStateException("You cannot remove your own active administrator account.");
        }

        // Clean up tokens
        passwordResetTokenRepository.deleteByUser(user);
        emailVerificationTokenRepository.deleteByUser(user);

        // Clear favorite rooms
        user.getFavoriteRooms().clear();
        userRepository.save(user);

        // Clean up bookings belonging to this user
        List<Booking> bookings = bookingRepository.findByUserOrderByCreatedAtDesc(user);
        for (Booking b : bookings) {
            paymentTransactionRepository.deleteAll(paymentTransactionRepository.findByBookingIdOrderByCreatedAtDesc(b.getId()));
            bookingStatusHistoryRepository.deleteAll(bookingStatusHistoryRepository.findByBookingIdOrderByChangedAtAsc(b.getId()));
            bookingRepository.delete(b);
        }

        userRepository.delete(user);
        log.info("Admin removed user id={} email={}", id, user.getEmail());
    }
}
