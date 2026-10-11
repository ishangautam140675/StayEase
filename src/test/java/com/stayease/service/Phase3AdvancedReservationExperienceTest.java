package com.stayease.service;

import com.stayease.dto.BookingForm;
import com.stayease.dto.PriceBreakdown;
import com.stayease.exception.BookingConflictException;
import com.stayease.exception.BookingValidationException;
import com.stayease.exception.InvalidBookingStateException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.BookingStatusHistory;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.BookingStatusHistoryRepository;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Phase3AdvancedReservationExperienceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingStatusHistoryRepository historyRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    private User testCustomer1;
    private User testCustomer2;
    private User testAdmin;
    private Room testRoom;

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        bookingRepository.deleteAll();

        testCustomer1 = userRepository.findByEmail("phase3_cust1@stayease.com").orElseGet(() -> {
            User u = new User("Cust One", "phase3_cust1@stayease.com", "secret123", "+1234567890", Role.CUSTOMER);
            return userRepository.save(u);
        });

        testCustomer2 = userRepository.findByEmail("phase3_cust2@stayease.com").orElseGet(() -> {
            User u = new User("Cust Two", "phase3_cust2@stayease.com", "secret123", "+1234567891", Role.CUSTOMER);
            return userRepository.save(u);
        });

        testAdmin = userRepository.findByEmail("phase3_admin@stayease.com").orElseGet(() -> {
            User u = new User("Admin Phase3", "phase3_admin@stayease.com", "secret123", "+1234567892", Role.ADMIN);
            return userRepository.save(u);
        });

        testRoom = roomRepository.findByRoomNumberIgnoreCase("P3-101").orElseGet(() -> {
            Room r = new Room("P3-101", RoomType.DOUBLE, 2, new BigDecimal("150.00"), "Phase 3 Test Room", true, false, "WiFi, King Bed, Balcony, Sea View");
            return roomRepository.save(r);
        });
        testRoom.setPricePerNight(new BigDecimal("150.00"));
        testRoom.setUnderMaintenance(false);
        testRoom.setAmenities("WiFi, King Bed, Balcony, Sea View");
        roomRepository.save(testRoom);
    }

    @AfterEach
    void tearDown() {
        historyRepository.deleteAll();
        bookingRepository.deleteAll();
    }

    @Test
    @DisplayName("Historical price preservation: changing room rate does not alter existing booking cost")
    void testHistoricalPricePreservation() {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = LocalDate.now().plusDays(13); // 3 nights @ 150 = 450
        BookingForm form = new BookingForm(testRoom.getId(), checkIn, checkOut, 2, "Test historical pricing");

        Booking booking = bookingService.createBooking(testCustomer1.getEmail(), form);
        assertThat(booking.getPricePerNightAtBooking()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(booking.getTotalPrice()).isEqualByComparingTo(new BigDecimal("450.00"));

        // Now admin doubles room price to 300.00
        testRoom.setPricePerNight(new BigDecimal("300.00"));
        roomRepository.save(testRoom);

        // Fetch booking again from DB
        Booking fetched = bookingService.getBookingWithDetails(booking.getId(), testCustomer1.getEmail());
        assertThat(fetched.getEffectivePricePerNight()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(fetched.getTotalPrice()).isEqualByComparingTo(new BigDecimal("450.00"));
    }

    @Test
    @DisplayName("Idempotent booking creation: repeated submissions with same idempotencyKey return identical booking")
    void testIdempotentBookingCreation() {
        LocalDate checkIn = LocalDate.now().plusDays(15);
        LocalDate checkOut = LocalDate.now().plusDays(17);
        String idempotencyKey = UUID.randomUUID().toString();

        BookingForm form1 = new BookingForm(testRoom.getId(), checkIn, checkOut, 2, "First submission");
        form1.setIdempotencyKey(idempotencyKey);
        Booking booking1 = bookingService.createBooking(testCustomer1.getEmail(), form1);

        BookingForm form2 = new BookingForm(testRoom.getId(), checkIn, checkOut, 2, "Duplicate submission");
        form2.setIdempotencyKey(idempotencyKey);
        Booking booking2 = bookingService.createBooking(testCustomer1.getEmail(), form2);

        assertThat(booking2.getId()).isEqualTo(booking1.getId());
        assertThat(booking2.getBookingReference()).isEqualTo(booking1.getBookingReference());
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Booking reference and status lifecycle audit history")
    void testBookingReferenceAndLifecycleAudit() {
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = LocalDate.now().plusDays(22);
        BookingForm form = new BookingForm(testRoom.getId(), checkIn, checkOut, 2, "Audit test");

        Booking booking = bookingService.createBooking(testCustomer1.getEmail(), form);
        assertThat(booking.getBookingReference()).startsWith("SE-");

        // Check initial creation audit
        List<BookingStatusHistory> history = historyRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getToStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(history.get(0).getChangedBy()).isEqualTo(testCustomer1.getEmail());

        // Admin approves booking
        Booking approved = bookingService.approve(booking.getId(), testAdmin.getEmail());
        assertThat(approved.getStatus()).isEqualTo(BookingStatus.APPROVED);

        history = historyRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());
        assertThat(history).hasSize(2);
        assertThat(history.get(1).getToStatus()).isEqualTo(BookingStatus.APPROVED);
        assertThat(history.get(1).getChangedBy()).isEqualTo(testAdmin.getEmail());

        // Customer cancels booking
        Booking cancelled = bookingService.cancel(booking.getId(), testCustomer1.getEmail());
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        history = historyRepository.findByBookingIdOrderByChangedAtAsc(booking.getId());
        assertThat(history).hasSize(3);
        assertThat(history.get(2).getToStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(history.get(2).getChangedBy()).isEqualTo(testCustomer1.getEmail());
    }

    @Test
    @DisplayName("Configurable cancellation policy: allowed before deadline, rejected within lead cutoff")
    void testCancellationPolicyEnforcement() {
        // Far in the future -> allowed
        LocalDate checkInFar = LocalDate.now().plusDays(7);
        LocalDate checkOutFar = LocalDate.now().plusDays(9);
        Booking bFar = bookingService.createBooking(
                testCustomer1.getEmail(),
                new BookingForm(testRoom.getId(), checkInFar, checkOutFar, 1, "Far booking")
        );
        assertThat(bFar.isCancellable(bookingService.getCancellationLeadHours())).isTrue();
        Booking cancelled = bookingService.cancel(bFar.getId(), testCustomer1.getEmail());
        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.CANCELLED);

        // Near booking (check-in today) is inside cutoff
        LocalDate checkInNear = LocalDate.now();
        LocalDate checkOutNear = LocalDate.now().plusDays(2);
        Booking bNear = bookingService.createBooking(
                testCustomer1.getEmail(),
                new BookingForm(testRoom.getId(), checkInNear, checkOutNear, 1, "Near booking")
        );
        assertThat(bNear.isCancellable(bookingService.getCancellationLeadHours())).isFalse();
        assertThatThrownBy(() -> bookingService.cancel(bNear.getId(), testCustomer1.getEmail()))
                .isInstanceOf(InvalidBookingStateException.class)
                .hasMessageContaining("cancel");
    }

    @Test
    @DisplayName("Maintenance room blocking: room under maintenance is excluded from search and cannot be booked")
    void testMaintenanceRoomBlocking() {
        Room maintRoom = roomRepository.findByRoomNumberIgnoreCase("P3-MAINT").orElseGet(() -> {
            Room r = new Room("P3-MAINT", RoomType.SINGLE, 4, new BigDecimal("250.00"), "Maintenance Suite", true, true, "WiFi, Jacuzzi");
            return roomRepository.save(r);
        });
        maintRoom.setUnderMaintenance(true);
        roomRepository.save(maintRoom);

        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = LocalDate.now().plusDays(7);

        // Verify excluded from available rooms search
        List<Room> available = roomRepository.findAvailableRooms(checkIn, checkOut, 1, BookingStatus.BLOCKING);
        assertThat(available).noneMatch(r -> r.getRoomNumber().equalsIgnoreCase("P3-MAINT"));

        // Verify booking throws BookingValidationException
        BookingForm form = new BookingForm(maintRoom.getId(), checkIn, checkOut, 2, "Try book maint");
        assertThatThrownBy(() -> bookingService.createBooking(testCustomer1.getEmail(), form))
                .isInstanceOf(BookingValidationException.class)
                .hasMessageContaining("maintenance");
    }

    @Test
    @DisplayName("Advanced room search: filter by checkIn, checkOut, capacity, roomType, price, and amenities")
    void testAdvancedRoomSearch() {
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = LocalDate.now().plusDays(33);

        Page<Room> result = roomRepository.searchRoomsAdvanced(
                RoomType.DOUBLE,
                2,
                new BigDecimal("100.00"),
                new BigDecimal("200.00"),
                "Balcony",
                checkIn,
                checkOut,
                BookingStatus.BLOCKING,
                PageRequest.of(0, 10)
        );

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent()).allMatch(r ->
                r.getType() == RoomType.DOUBLE &&
                r.getCapacity() >= 2 &&
                r.hasAmenity("Balcony") &&
                !r.isUnderMaintenance()
        );
    }

    @Test
    @DisplayName("Adjacent dates succeed without conflict while overlapping dates are rejected")
    void testAdjacentDatesVsOverlappingDates() {
        LocalDate checkIn1 = LocalDate.now().plusDays(40);
        LocalDate checkOut1 = LocalDate.now().plusDays(42);
        Booking b1 = bookingService.createBooking(testCustomer1.getEmail(), new BookingForm(testRoom.getId(), checkIn1, checkOut1, 2, "B1"));
        assertThat(b1.getId()).isNotNull();

        // Adjacent: checkIn exactly matches b1's checkOut -> MUST succeed
        LocalDate checkIn2 = checkOut1;
        LocalDate checkOut2 = checkIn2.plusDays(2);
        Booking b2 = bookingService.createBooking(testCustomer2.getEmail(), new BookingForm(testRoom.getId(), checkIn2, checkOut2, 2, "B2 adjacent"));
        assertThat(b2.getId()).isNotNull();

        // Overlapping: intersects b1 -> MUST fail
        LocalDate checkInOverlap = checkIn1.plusDays(1);
        LocalDate checkOutOverlap = checkOut1.plusDays(1);
        assertThatThrownBy(() -> bookingService.createBooking(
                testCustomer1.getEmail(),
                new BookingForm(testRoom.getId(), checkInOverlap, checkOutOverlap, 2, "B3 overlap")
        )).isInstanceOf(BookingConflictException.class);
    }

    @Test
    @DisplayName("Concurrency regression test: simultaneous booking requests on the same room yield exactly 1 success")
    void testConcurrentOverlappingBookings() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(50);
        LocalDate checkOut = LocalDate.now().plusDays(53);

        int threads = 6;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch readyLatch = new CountDownLatch(threads);
        CountDownLatch startLatch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Future<Void>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                User user = (index % 2 == 0) ? testCustomer1 : testCustomer2;
                BookingForm form = new BookingForm(testRoom.getId(), checkIn, checkOut, 1, "Concurrent test " + index);
                try {
                    bookingService.createBooking(user.getEmail(), form);
                    successCount.incrementAndGet();
                } catch (BookingConflictException e) {
                    conflictCount.incrementAndGet();
                }
                return null;
            }));
        }

        readyLatch.await(10, TimeUnit.SECONDS);
        startLatch.countDown();

        for (Future<Void> f : futures) {
            f.get(30, TimeUnit.SECONDS);
        }
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("Price breakdown calculation provides transparent itemized rates and nights")
    void testPriceBreakdownCalculation() {
        LocalDate checkIn = LocalDate.now().plusDays(60);
        LocalDate checkOut = LocalDate.now().plusDays(64); // 4 nights @ 150 = 600

        PriceBreakdown breakdown = bookingService.calculatePriceBreakdown(testRoom, checkIn, checkOut);
        assertThat(breakdown.getNights()).isEqualTo(4);
        assertThat(breakdown.getSubtotal()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(breakdown.getTotalPrice()).isEqualByComparingTo(new BigDecimal("600.00"));
        assertThat(breakdown.getNightlyItems()).hasSize(4);
    }
}
