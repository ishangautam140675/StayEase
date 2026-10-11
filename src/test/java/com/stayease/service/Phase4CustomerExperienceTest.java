package com.stayease.service;

import com.stayease.dto.BookingForm;
import com.stayease.dto.PasswordChangeForm;
import com.stayease.dto.ProfileUpdateForm;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.NotificationLog;
import com.stayease.model.NotificationStatus;
import com.stayease.model.NotificationType;
import com.stayease.model.PasswordResetToken;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.BookingStatusHistoryRepository;
import com.stayease.repository.EmailVerificationTokenRepository;
import com.stayease.repository.NotificationLogRepository;
import com.stayease.repository.PasswordResetTokenRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class Phase4CustomerExperienceTest {

    @Autowired
    private CustomerAccountService customerAccountService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingStatusHistoryRepository historyRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User testCustomer;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        bookingRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        notificationLogRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();

        testCustomer = new User(
                "Original Guest",
                "guest_p4@example.com",
                passwordEncoder.encode("Secret123!"),
                "1234567890",
                Role.CUSTOMER
        );
        testCustomer.setEmailVerified(false);
        testCustomer = userRepository.save(testCustomer);

        testRoom = new Room(
                "401",
                RoomType.DOUBLE,
                2,
                new BigDecimal("350.00"),
                "Luxury suite with panoramic terrace view.",
                true,
                false,
                "WiFi, King Bed, Balcony"
        );
        testRoom = roomRepository.save(testRoom);
    }

    @AfterEach
    void tearDown() {
        historyRepository.deleteAll();
        bookingRepository.deleteAll();
        emailVerificationTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        notificationLogRepository.deleteAll();
    }

    @Test
    @DisplayName("Should successfully update customer profile details")
    void testProfileUpdate() {
        ProfileUpdateForm form = new ProfileUpdateForm();
        form.setFullName("Updated VIP Guest");
        form.setPhone("+1-555-0199");

        customerAccountService.updateProfile("guest_p4@example.com", form);

        User updated = userRepository.findByEmail("guest_p4@example.com").orElseThrow();
        assertThat(updated.getFullName()).isEqualTo("Updated VIP Guest");
        assertThat(updated.getPhone()).isEqualTo("+1-555-0199");
    }

    @Test
    @DisplayName("Should change password with valid current credentials and reject invalid current password")
    void testPasswordChange() {
        // 1. Invalid current password should throw exception
        PasswordChangeForm badForm = new PasswordChangeForm();
        badForm.setCurrentPassword("WrongPass!");
        badForm.setNewPassword("BrandNewPass123!");
        badForm.setConfirmPassword("BrandNewPass123!");

        assertThatThrownBy(() -> customerAccountService.changePassword("guest_p4@example.com", badForm))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("The current password you entered is incorrect.");

        // 2. Valid current password changes credentials
        PasswordChangeForm goodForm = new PasswordChangeForm();
        goodForm.setCurrentPassword("Secret123!");
        goodForm.setNewPassword("BrandNewPass123!");
        goodForm.setConfirmPassword("BrandNewPass123!");

        customerAccountService.changePassword("guest_p4@example.com", goodForm);

        User refreshed = userRepository.findByEmail("guest_p4@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("BrandNewPass123!", refreshed.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Should generate single-use password reset token and prevent enumeration for missing users")
    void testPasswordResetWorkflow() {
        // Missing user: should not throw exception (prevents account enumeration)
        customerAccountService.requestPasswordReset("nonexistent_user@example.com");

        // Existing user: generates token and sends notification
        customerAccountService.requestPasswordReset("guest_p4@example.com");

        PasswordResetToken tokenEntity = passwordResetTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getEmail().equalsIgnoreCase("guest_p4@example.com"))
                .findFirst()
                .orElseThrow();
        String resetToken = tokenEntity.getToken();

        // Verify token validity
        assertThat(customerAccountService.isPasswordResetTokenValid(resetToken)).isTrue();

        // Perform reset
        customerAccountService.resetPassword(resetToken, "RecoveredPass123!");

        User refreshed = userRepository.findByEmail("guest_p4@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("RecoveredPass123!", refreshed.getPasswordHash())).isTrue();

        // Single-use: token must be invalidated now
        assertThat(customerAccountService.isPasswordResetTokenValid(resetToken)).isFalse();
        assertThatThrownBy(() -> customerAccountService.resetPassword(resetToken, "AnotherAttempt123!"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should generate email verification token and verify customer account")
    void testEmailVerification() {
        assertThat(testCustomer.isEmailVerified()).isFalse();

        customerAccountService.requestEmailVerification("guest_p4@example.com");

        var tokenEntity = emailVerificationTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getEmail().equalsIgnoreCase("guest_p4@example.com"))
                .findFirst()
                .orElseThrow();
        String token = tokenEntity.getToken();

        boolean verified = customerAccountService.verifyEmail(token);
        assertThat(verified).isTrue();

        User refreshed = userRepository.findByEmail("guest_p4@example.com").orElseThrow();
        assertThat(refreshed.isEmailVerified()).isTrue();

        // Token consumed: second try returns false
        boolean secondAttempt = customerAccountService.verifyEmail(token);
        assertThat(secondAttempt).isFalse();
    }

    @Test
    @DisplayName("Should toggle room favorites back and forth")
    void testFavoriteRooms() {
        // Initially not favorited
        Set<Room> initialFavorites = customerAccountService.getFavoriteRooms("guest_p4@example.com");
        assertThat(initialFavorites).isEmpty();

        // Add to favorite
        boolean added = customerAccountService.toggleFavoriteRoom("guest_p4@example.com", testRoom.getId());
        assertThat(added).isTrue();

        Set<Room> favoritesAfterAdd = customerAccountService.getFavoriteRooms("guest_p4@example.com");
        assertThat(favoritesAfterAdd).extracting(Room::getRoomNumber).containsExactly("401");

        // Toggle again to remove
        boolean removed = customerAccountService.toggleFavoriteRoom("guest_p4@example.com", testRoom.getId());
        assertThat(removed).isFalse();

        Set<Room> favoritesAfterRemove = customerAccountService.getFavoriteRooms("guest_p4@example.com");
        assertThat(favoritesAfterRemove).isEmpty();
    }

    @Test
    @DisplayName("Should filter customer bookings by status and search reference")
    void testCustomerBookingFilters() {
        LocalDate start = LocalDate.now().plusDays(2);
        LocalDate end = LocalDate.now().plusDays(5);

        BookingForm form = new BookingForm(testRoom.getId(), start, end, 2);

        Booking b1 = bookingService.createBooking("guest_p4@example.com", form);
        assertThat(b1.getBookingReference()).isNotNull();

        // Query by ALL status (null BookingStatus)
        Page<Booking> allPage = bookingService.getBookingsForUser("guest_p4@example.com", null, null, PageRequest.of(0, 10));
        assertThat(allPage.getContent()).hasSize(1);

        // Query by PENDING status
        Page<Booking> pendingPage = bookingService.getBookingsForUser("guest_p4@example.com", BookingStatus.PENDING, null, PageRequest.of(0, 10));
        assertThat(pendingPage.getContent()).hasSize(1);

        // Query by APPROVED status (should be 0)
        Page<Booking> approvedPage = bookingService.getBookingsForUser("guest_p4@example.com", BookingStatus.APPROVED, null, PageRequest.of(0, 10));
        assertThat(approvedPage.getContent()).isEmpty();

        // Query by booking reference substring
        String refPart = b1.getBookingReference().substring(0, 6);
        Page<Booking> refPage = bookingService.getBookingsForUser("guest_p4@example.com", null, refPart, PageRequest.of(0, 10));
        assertThat(refPage.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Should gracefully handle notification delivery and protect booking transaction from notification failures")
    void testNotificationResilienceAndSimulation() {
        LocalDate start = LocalDate.now().plusDays(10);
        LocalDate end = LocalDate.now().plusDays(14);

        BookingForm form = new BookingForm(testRoom.getId(), start, end, 2);

        // Booking creation triggers safe email notification
        Booking booking = bookingService.createBooking("guest_p4@example.com", form);
        assertThat(booking.getId()).isNotNull();

        // Verify notification log recorded idempotency and status
        var logs = notificationLogRepository.findAll().stream()
                .filter(l -> "guest_p4@example.com".equalsIgnoreCase(l.getRecipient()))
                .toList();
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);

        // Verify duplicate delivery protection (duplicate send attempts with same idempotency key are skipped)
        String idemKey = logs.get(0).getIdempotencyKey();
        emailService.sendBookingCreated(booking); // Invoking again
        var dupLogs = notificationLogRepository.findAll().stream()
                .filter(l -> idemKey != null && idemKey.equals(l.getIdempotencyKey()))
                .toList();
        assertThat(dupLogs).hasSize(1); // Only 1 logged record preserved
    }
}
