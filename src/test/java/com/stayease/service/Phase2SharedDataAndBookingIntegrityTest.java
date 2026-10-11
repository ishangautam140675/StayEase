package com.stayease.service;

import com.stayease.dto.BookingForm;
import com.stayease.exception.BookingConflictException;
import com.stayease.exception.BookingValidationException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomMaintenanceSchedule;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomMaintenanceScheduleRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 — Shared Data and Booking Integrity Test Suite.
 *
 * Verifies:
 * 1. Single source of truth / shared database state persistence.
 * 2. Database-level locking (PESSIMISTIC_WRITE) serializing concurrent booking transactions.
 * 3. Exact-one-winner concurrency for overlapping dates on the same room.
 * 4. Concurrent requests for non-overlapping dates or distinct rooms both succeed.
 * 5. Idempotent booking creation preventing duplicate records on duplicate submission.
 * 6. Server-side validation of business rules (past dates, stay length, capacity, inactive rooms).
 * 7. Scheduled room maintenance blocking reservations during maintenance intervals.
 * 8. Complete lifecycle consistency (PENDING -> APPROVED -> CHECKED_IN -> CHECKED_OUT).
 * 9. Cancellation and rejection releasing date blocking immediately.
 * 10. Transactional atomicity: failed attempts leave zero phantom records.
 */
@SpringBootTest
class Phase2SharedDataAndBookingIntegrityTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomMaintenanceScheduleRepository maintenanceScheduleRepository;

    private User guestAlpha;
    private User guestBeta;
    private Room suiteRoom;
    private Room deluxeRoom;

    @BeforeEach
    void setUp() {
        maintenanceScheduleRepository.deleteAll();
        bookingRepository.deleteAll();
        roomRepository.deleteAll();

        userRepository.findByEmail("phase2.alpha@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("phase2.beta@stayease.com").ifPresent(userRepository::delete);

        guestAlpha = userRepository.save(new User(
                "Guest Alpha",
                "phase2.alpha@stayease.com",
                "Password@123",
                "9876543210",
                Role.CUSTOMER
        ));

        guestBeta = userRepository.save(new User(
                "Guest Beta",
                "phase2.beta@stayease.com",
                "Password@123",
                "9876543211",
                Role.CUSTOMER
        ));

        suiteRoom = roomRepository.save(new Room(
                "SUITE-101",
                RoomType.TRIPLE,
                3,
                new BigDecimal("1200.00"),
                "Phase 2 Suite Room",
                true
        ));

        deluxeRoom = roomRepository.save(new Room(
                "DELUXE-102",
                RoomType.DOUBLE,
                2,
                new BigDecimal("800.00"),
                "Phase 2 Deluxe Room",
                true
        ));
    }

    @AfterEach
    void tearDown() {
        maintenanceScheduleRepository.deleteAll();
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.findByEmail("phase2.alpha@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("phase2.beta@stayease.com").ifPresent(userRepository::delete);
    }

    @Test
    @DisplayName("Concurrency: Two threads booking the same room for overlapping dates results in exactly one success and one conflict")
    void testConcurrentBooking_overlappingDates_serializesViaLocking_exactlyOneSucceeds() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(15);
        LocalDate checkOut = LocalDate.now().plusDays(19);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = Collections.synchronizedList(new ArrayList<>());

        Callable<Void> taskAlpha = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            try {
                bookingService.createBooking(guestAlpha.getEmail(), new BookingForm(suiteRoom.getId(), checkIn, checkOut, 2));
                successCount.incrementAndGet();
            } catch (BookingConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
            return null;
        };

        Callable<Void> taskBeta = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            try {
                bookingService.createBooking(guestBeta.getEmail(), new BookingForm(suiteRoom.getId(), checkIn, checkOut, 2));
                successCount.incrementAndGet();
            } catch (BookingConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                unexpectedErrors.add(t);
            }
            return null;
        };

        Future<Void> f1 = executor.submit(taskAlpha);
        Future<Void> f2 = executor.submit(taskBeta);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Fire both threads simultaneously

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(unexpectedErrors).as("Expected zero unexpected exceptions during race").isEmpty();
        assertThat(successCount.get()).as("Exactly one concurrent booking transaction must commit successfully").isEqualTo(1);
        assertThat(conflictCount.get()).as("The competing transaction must be rejected with BookingConflictException").isEqualTo(1);

        List<Booking> saved = bookingRepository.findOverlapping(suiteRoom.getId(), checkIn, checkOut, BookingStatus.BLOCKING);
        assertThat(saved).as("Database must contain exactly 1 blocking booking for this room/interval").hasSize(1);
    }

    @Test
    @DisplayName("Concurrency: Simultaneous booking requests for non-overlapping dates on the same room both succeed")
    void testConcurrentBooking_nonOverlappingDates_bothSucceed() throws Exception {
        LocalDate stay1In = LocalDate.now().plusDays(20);
        LocalDate stay1Out = LocalDate.now().plusDays(24);
        LocalDate stay2In = LocalDate.now().plusDays(24); // Adjacent turnover
        LocalDate stay2Out = LocalDate.now().plusDays(28);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            try {
                bookingService.createBooking(guestAlpha.getEmail(), new BookingForm(suiteRoom.getId(), stay1In, stay1Out, 2));
                successCount.incrementAndGet();
            } catch (Throwable t) {
                errors.add(t);
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            try {
                bookingService.createBooking(guestBeta.getEmail(), new BookingForm(suiteRoom.getId(), stay2In, stay2Out, 2));
                successCount.incrementAndGet();
            } catch (Throwable t) {
                errors.add(t);
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown();

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(errors).isEmpty();
        assertThat(successCount.get()).as("Both non-overlapping reservations must succeed").isEqualTo(2);

        List<Booking> roomBookings = bookingRepository.findByUserOrderByCreatedAtDesc(guestAlpha);
        assertThat(roomBookings).hasSize(1);
    }

    @Test
    @DisplayName("Idempotency: Re-submitting the same booking request with the same idempotency key returns original booking without duplicates")
    void testIdempotency_duplicateSubmission_returnsOriginalBooking() {
        String idempotencyKey = "IDEM-" + UUID.randomUUID();
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(8);

        BookingForm form1 = new BookingForm(suiteRoom.getId(), checkIn, checkOut, 2);
        form1.setIdempotencyKey(idempotencyKey);

        Booking firstResult = bookingService.createBooking(guestAlpha.getEmail(), form1);
        assertThat(firstResult).isNotNull();
        assertThat(firstResult.getId()).isNotNull();
        assertThat(firstResult.getIdempotencyKey()).isEqualTo(idempotencyKey);

        // Resubmit identical form with same idempotency key
        BookingForm form2 = new BookingForm(suiteRoom.getId(), checkIn, checkOut, 2);
        form2.setIdempotencyKey(idempotencyKey);

        Booking secondResult = bookingService.createBooking(guestAlpha.getEmail(), form2);
        assertThat(secondResult).isNotNull();
        assertThat(secondResult.getId()).isEqualTo(firstResult.getId());
        assertThat(secondResult.getBookingReference()).isEqualTo(firstResult.getBookingReference());

        // Verify total records in database for this room is exactly 1
        long count = bookingRepository.count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Validation: Business rules enforced on server (past dates, invalid range, guest capacity)")
    void testBusinessRuleValidation_enforcesAllConstraints() {
        LocalDate today = LocalDate.now();

        // 1. Past check-in date
        BookingForm pastForm = new BookingForm(suiteRoom.getId(), today.minusDays(1), today.plusDays(2), 1);
        assertThatThrownBy(() -> bookingService.createBooking(guestAlpha.getEmail(), pastForm))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("past");

        // 2. Check-out date before or equal to check-in
        BookingForm invalidDatesForm = new BookingForm(suiteRoom.getId(), today.plusDays(3), today.plusDays(3), 1);
        assertThatThrownBy(() -> bookingService.createBooking(guestAlpha.getEmail(), invalidDatesForm))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("after");

        // 3. Guest count exceeding room capacity (suiteRoom capacity = 3, requesting 4)
        BookingForm overCapacityForm = new BookingForm(suiteRoom.getId(), today.plusDays(3), today.plusDays(6), 4);
        assertThatThrownBy(() -> bookingService.createBooking(guestAlpha.getEmail(), overCapacityForm))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("exceeds");

        // 4. Inactive room cannot be booked
        Room inactiveRoom = roomRepository.save(new Room("INACT-999", RoomType.SINGLE, 1, new BigDecimal("400.00"), "Inactive", false));
        BookingForm inactiveRoomForm = new BookingForm(inactiveRoom.getId(), today.plusDays(3), today.plusDays(6), 1);
        assertThatThrownBy(() -> bookingService.createBooking(guestAlpha.getEmail(), inactiveRoomForm))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("inactive");
    }

    @Test
    @DisplayName("Maintenance Conflict: Scheduled room maintenance blocks reservation during maintenance period")
    void testMaintenanceSchedule_blocksBookingDuringScheduledDates() {
        LocalDate maintStart = LocalDate.now().plusDays(10);
        LocalDate maintEnd = LocalDate.now().plusDays(14);

        maintenanceScheduleRepository.save(new RoomMaintenanceSchedule(
                suiteRoom,
                maintStart,
                maintEnd,
                "Annual plumbing overhaul",
                "admin@stayease.com"
        ));

        // Overlapping request (days 11 to 13)
        BookingForm overlapForm = new BookingForm(suiteRoom.getId(), LocalDate.now().plusDays(11), LocalDate.now().plusDays(13), 2);

        assertThatThrownBy(() -> bookingService.createBooking(guestAlpha.getEmail(), overlapForm))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("maintenance")
                .hasMessageContaining("Annual plumbing overhaul");
    }

    @Test
    @DisplayName("Lifecycle: PENDING -> APPROVED -> CHECKED_IN -> CHECKED_OUT transitions and state consistency")
    void testBookingLifecycle_transitionsAndStateConsistency() {
        LocalDate checkIn = LocalDate.now().plusDays(3);
        LocalDate checkOut = LocalDate.now().plusDays(6);

        Booking created = bookingService.createBooking(guestAlpha.getEmail(), new BookingForm(suiteRoom.getId(), checkIn, checkOut, 2));
        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING);

        // Approve
        Booking approved = bookingService.approve(created.getId(), "admin@stayease.com");
        assertThat(approved.getStatus()).isEqualTo(BookingStatus.APPROVED);

        // Check in
        Booking checkedIn = bookingService.checkIn(approved.getId(), "admin@stayease.com");
        assertThat(checkedIn.getStatus()).isEqualTo(BookingStatus.CHECKED_IN);
        assertThat(checkedIn.getCheckedInAt()).isNotNull();

        // Check out
        Booking checkedOut = bookingService.checkOut(checkedIn.getId(), "admin@stayease.com");
        assertThat(checkedOut.getStatus()).isEqualTo(BookingStatus.CHECKED_OUT);
        assertThat(checkedOut.getCheckedOutAt()).isNotNull();

        // CHECKED_OUT status releases the date block
        List<Booking> blocking = bookingRepository.findOverlapping(suiteRoom.getId(), checkIn, checkOut, BookingStatus.BLOCKING);
        assertThat(blocking).as("Checked-out booking does not block future bookings").isEmpty();

        // Another guest can now reserve that room for the same dates
        Booking newReservation = bookingService.createBooking(guestBeta.getEmail(), new BookingForm(suiteRoom.getId(), checkIn, checkOut, 2));
        assertThat(newReservation.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test
    @DisplayName("Cancellation: Customer cancellation immediately releases inventory for other guests")
    void testCancellation_releasesInventoryImmediately() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(9);

        Booking created = bookingService.createBooking(guestAlpha.getEmail(), new BookingForm(deluxeRoom.getId(), checkIn, checkOut, 1));
        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING);

        // Customer cancels
        Booking cancelled = bookingService.cancel(created.getId(), guestAlpha.getEmail());
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // deluxeRoom is immediately available for guestBeta
        Booking replacement = bookingService.createBooking(guestBeta.getEmail(), new BookingForm(deluxeRoom.getId(), checkIn, checkOut, 1));
        assertThat(replacement).isNotNull();
        assertThat(replacement.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(replacement.getUser().getEmail()).isEqualTo(guestBeta.getEmail());
    }

    @Test
    @DisplayName("Admin Rejection: Rejecting a pending booking immediately frees dates for subsequent reservation")
    void testRejection_freesDatesImmediately() {
        LocalDate checkIn = LocalDate.now().plusDays(8);
        LocalDate checkOut = LocalDate.now().plusDays(12);

        Booking created = bookingService.createBooking(guestAlpha.getEmail(), new BookingForm(deluxeRoom.getId(), checkIn, checkOut, 2));
        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING);

        // Admin rejects
        Booking rejected = bookingService.reject(created.getId(), "Guest details incomplete", "admin@stayease.com");
        assertThat(rejected.getStatus()).isEqualTo(BookingStatus.REJECTED);
        assertThat(rejected.getAdminRemark()).isEqualTo("Guest details incomplete");

        // guestBeta can book the same dates
        Booking newBooking = bookingService.createBooking(guestBeta.getEmail(), new BookingForm(deluxeRoom.getId(), checkIn, checkOut, 2));
        assertThat(newBooking).isNotNull();
        assertThat(newBooking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }
}
