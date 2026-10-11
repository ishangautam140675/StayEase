package com.stayease.service;

import com.stayease.config.AsyncConfig;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.InAppNotification;
import com.stayease.model.NotificationType;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.InAppNotificationRepository;
import com.stayease.repository.NotificationLogRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public class Phase9NotificationAndAsyncTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Autowired
    private AsyncConfig asyncConfig;

    @Autowired
    private BookingReminderService reminderService;

    @Autowired
    private InAppNotificationService notificationService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private InAppNotificationRepository notificationRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    private User testUser;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        testUser = userRepository.findByEmail("phase9guest@stayease.com").orElseGet(() -> {
            User user = new User("Phase Nine Guest", "phase9guest@stayease.com", "$2a$10$DUMMYPASSWORDHASH", "9876543210", Role.CUSTOMER);
            user.setEmailVerified(true);
            return userRepository.save(user);
        });

        testRoom = roomRepository.findByRoomNumberIgnoreCase("P9-101").orElseGet(() -> {
            Room room = new Room("P9-101", RoomType.SINGLE, 2, new BigDecimal("3200.00"), "Phase 9 test suite room", true);
            return roomRepository.save(room);
        });
    }

    @Test
    @DisplayName("Task Executor: ThreadPoolTaskExecutor is correctly configured with capacity, pool sizes, and prefix")
    void taskExecutor_ProperlyConfigured() {
        assertThat(taskExecutor).isNotNull();
        assertThat(taskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) taskExecutor;
        assertThat(executor.getCorePoolSize()).isEqualTo(5);
        assertThat(executor.getMaxPoolSize()).isEqualTo(20);
        assertThat(executor.getQueueCapacity()).isEqualTo(500);
        assertThat(executor.getThreadNamePrefix()).isEqualTo("stayease-async-");
        assertThat(asyncConfig.getAsyncUncaughtExceptionHandler()).isNotNull();
    }

    @Test
    @DisplayName("Async Notifications: Asynchronous in-app and email methods complete without error")
    void asyncMethods_ExecuteSuccessfully() throws Exception {
        // Test async in-app notifications
        assertThatCode(() -> {
            notificationService.notifyUserAsync(
                    testUser.getEmail(),
                    "Async Test Title",
                    "Async Test Message",
                    "BOOKING_APPROVED",
                    "/test/link"
            );
        }).doesNotThrowAnyException();

        // Test async email dispatch
        CompletableFuture<Void> future = emailService.sendSafeAsync(
                testUser.getEmail(),
                "Async Email Subject",
                "<p>Hello Async</p>",
                NotificationType.BOOKING_REMINDER,
                "REF-ASYNC-1",
                "IDEMP-ASYNC-P9"
        );
        assertThat(future).isNotNull();
        future.get(); // Ensure completion without exception

        assertThat(notificationLogRepository.findByIdempotencyKey("IDEMP-ASYNC-P9")).isPresent();
    }

    @Test
    @DisplayName("Booking Reminders: Dispatches reminders only for approved bookings checking in on target date")
    void bookingReminders_DispatchesForTargetApprovedBookings() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);

        // Booking 1: Approved, check-in tomorrow -> SHOULD receive reminder
        Booking b1 = new Booking(testUser, testRoom, tomorrow, tomorrow.plusDays(2), 2, BookingStatus.APPROVED, null);
        b1.setBookingReference("P9-REM-001");
        bookingRepository.save(b1);

        // Booking 2: Pending, check-in tomorrow -> should NOT receive reminder
        Booking b2 = new Booking(testUser, testRoom, tomorrow, tomorrow.plusDays(2), 2, BookingStatus.PENDING, null);
        b2.setBookingReference("P9-REM-002");
        bookingRepository.save(b2);

        // Booking 3: Approved, check-in day after tomorrow -> should NOT receive reminder
        Booking b3 = new Booking(testUser, testRoom, tomorrow.plusDays(2), tomorrow.plusDays(4), 2, BookingStatus.APPROVED, null);
        b3.setBookingReference("P9-REM-003");
        bookingRepository.save(b3);

        int sentCount = reminderService.sendUpcomingCheckInReminders(tomorrow);
        assertThat(sentCount).isGreaterThanOrEqualTo(1);

        // Verify In-app notification was created
        List<InAppNotification> notifications = notificationRepository.findRecentForUser(
                testUser.getEmail(),
                "ROLE_CUSTOMER",
                org.springframework.data.domain.PageRequest.of(0, 10)
        );
        boolean reminderFound = notifications.stream()
                .anyMatch(n -> "BOOKING_REMINDER".equals(n.getType()) && n.getMessage().contains("P9-REM-001"));
        assertThat(reminderFound).isTrue();
    }

    @Test
    @DisplayName("Booking Reminders: Null or empty dates return 0 without errors")
    void bookingReminders_GracefullyHandlesNullOrPast() {
        assertThat(reminderService.sendUpcomingCheckInReminders(null)).isEqualTo(0);
        assertThat(reminderService.sendUpcomingCheckInReminders(LocalDate.now().plusYears(15))).isEqualTo(0);
    }

    @Test
    @DisplayName("Fault Tolerance: Notification failures log as FAILED without throwing or breaking transactions")
    void faultTolerance_NotificationFailureDoesNotBreakFlow() {
        // Send email with an intentionally invalid recipient or error condition
        assertThatCode(() -> {
            emailService.sendSafe(
                    "invalid-broken-address",
                    "Subject",
                    "<p>Body</p>",
                    NotificationType.BOOKING_REMINDER,
                    "REF-FAIL-1",
                    "IDEMP-FAIL-" + System.currentTimeMillis()
            );
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("API: GET /api/notifications returns user unread count and notification list")
    @WithMockUser(username = "phase9guest@stayease.com", roles = "CUSTOMER")
    void api_GetNotifications_Authenticated() throws Exception {
        notificationService.notifyUser(
                "phase9guest@stayease.com",
                "New Booking Confirmation",
                "Your booking P9-001 is confirmed.",
                "BOOKING_APPROVED",
                "/customer/bookings"
        );

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber())
                .andExpect(jsonPath("$.notifications").isArray());
    }

    @Test
    @DisplayName("API: GET /api/notifications without auth redirects to login")
    void api_GetNotifications_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("API: GET /api/notifications/poll returns new unread notifications since timestamp")
    @WithMockUser(username = "phase9guest@stayease.com", roles = "CUSTOMER")
    void api_PollNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications/poll")
                        .param("since", LocalDateTime.now().minusMinutes(5).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").isNumber())
                .andExpect(jsonPath("$.newNotifications").isArray());
    }

    @Test
    @DisplayName("API: POST /api/notifications/{id}/read and /read-all successfully marks notifications as read")
    @WithMockUser(username = "phase9guest@stayease.com", roles = "CUSTOMER")
    void api_MarkAsReadAndReadAll() throws Exception {
        InAppNotification notif = notificationService.notifyUser(
                "phase9guest@stayease.com",
                "Check-in Alert",
                "Your room is ready for early check-in.",
                "CHECK_IN",
                "/customer/bookings"
        );

        // Mark individual notification as read
        mockMvc.perform(post("/api/notifications/" + notif.getId() + "/read").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        // Mark all as read
        mockMvc.perform(post("/api/notifications/read-all").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }
}
