package com.stayease.service;

import com.stayease.dto.BookingForm;
import com.stayease.exception.BookingConflictException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BookingOverlapTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RoomService roomService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    private User customer1;
    private User customer2;
    private Room roomA;
    private Room roomB;
    private Booking approvedBooking;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.findByEmail("overlap.c1@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("overlap.c2@stayease.com").ifPresent(userRepository::delete);

        customer1 = userRepository.save(new User(
                "Customer One",
                "overlap.c1@stayease.com",
                "Password123",
                "1111111111",
                Role.CUSTOMER
        ));

        customer2 = userRepository.save(new User(
                "Customer Two",
                "overlap.c2@stayease.com",
                "Password123",
                "2222222222",
                Role.CUSTOMER
        ));

        roomA = roomRepository.save(new Room(
                "201-A",
                RoomType.DOUBLE,
                2,
                new BigDecimal("800.00"),
                "Room A",
                true
        ));

        roomB = roomRepository.save(new Room(
                "202-B",
                RoomType.DOUBLE,
                2,
                new BigDecimal("850.00"),
                "Room B",
                true
        ));

        // Existing APPROVED booking on roomA for days 10 to 14 from now
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = LocalDate.now().plusDays(14);
        approvedBooking = bookingRepository.save(new Booking(
                customer1,
                roomA,
                checkIn,
                checkOut,
                1,
                BookingStatus.APPROVED,
                null
        ));
    }

    @AfterEach
    void tearDown() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.findByEmail("overlap.c1@stayease.com").ifPresent(userRepository::delete);
        userRepository.findByEmail("overlap.c2@stayease.com").ifPresent(userRepository::delete);
    }

    // ── 1. Overlap Rejections ──────────────────────────────────────────────

    @ParameterizedTest(name = "[{index}] Overlapping stay days {0} to {1} is rejected")
    @CsvSource({
            "10, 14", // Same dates
            "11, 13", // Stay inside existing booking
            "9,  15", // Stay completely covering existing booking
            "8,  12", // Partial overlap on the left (starts before, ends inside)
            "12, 16"  // Partial overlap on the right (starts inside, ends after)
    })
    @DisplayName("Same dates, stay inside, stay covering, and partial overlaps on either side are rejected")
    void overlappingStays_areRejected(int startOffset, int endOffset) {
        LocalDate checkIn = LocalDate.now().plusDays(startOffset);
        LocalDate checkOut = LocalDate.now().plusDays(endOffset);

        BookingForm form = new BookingForm(roomA.getId(), checkIn, checkOut, 1);

        assertThatThrownBy(() -> bookingService.createBooking(customer2.getEmail(), form))
                .isInstanceOf(BookingConflictException.class)
                .satisfies(ex -> {
                    BookingConflictException bce = (BookingConflictException) ex;
                    assertThat(bce.getConflictingBookings()).isNotEmpty();
                    assertThat(bce.getConflictingBookings().get(0).getId()).isEqualTo(approvedBooking.getId());
                });
    }

    // ── 2. Adjacent Stays Accepted ─────────────────────────────────────────

    @Test
    @DisplayName("A stay that ends on day 10 and a stay that starts on day 14 are accepted (touching boundaries do not overlap)")
    void adjacentStays_areAccepted() {
        // Stay ending on day 10 (day 7 to 10)
        LocalDate stay1CheckIn = LocalDate.now().plusDays(7);
        LocalDate stay1CheckOut = LocalDate.now().plusDays(10);
        Booking booking1 = bookingService.createBooking(
                customer2.getEmail(),
                new BookingForm(roomA.getId(), stay1CheckIn, stay1CheckOut, 1)
        );
        assertThat(booking1).isNotNull();
        assertThat(booking1.getStatus()).isEqualTo(BookingStatus.PENDING);

        // Stay starting on day 14 (day 14 to 18)
        LocalDate stay2CheckIn = LocalDate.now().plusDays(14);
        LocalDate stay2CheckOut = LocalDate.now().plusDays(18);
        Booking booking2 = bookingService.createBooking(
                customer2.getEmail(),
                new BookingForm(roomA.getId(), stay2CheckIn, stay2CheckOut, 1)
        );
        assertThat(booking2).isNotNull();
        assertThat(booking2.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ── 3. Status Blocking Rules ───────────────────────────────────────────

    @Test
    @DisplayName("A PENDING booking also blocks dates")
    void pendingBooking_blocksDates() {
        bookingRepository.deleteAll();

        // Create PENDING booking for days 10 to 14
        bookingRepository.save(new Booking(
                customer1,
                roomA,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(14),
                1,
                BookingStatus.PENDING,
                null
        ));

        BookingForm form = new BookingForm(
                roomA.getId(),
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(14),
                1
        );

        assertThatThrownBy(() -> bookingService.createBooking(customer2.getEmail(), form))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    @DisplayName("REJECTED and CANCELLED bookings do not block dates")
    void rejectedAndCancelledBookings_doNotBlockDates() {
        bookingRepository.deleteAll();

        // 1. REJECTED booking does not block
        bookingRepository.save(new Booking(
                customer1,
                roomA,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(14),
                1,
                BookingStatus.REJECTED,
                "Rejected by admin"
        ));

        Booking b1 = bookingService.createBooking(
                customer2.getEmail(),
                new BookingForm(roomA.getId(), LocalDate.now().plusDays(10), LocalDate.now().plusDays(14), 1)
        );
        assertThat(b1).isNotNull();
        assertThat(b1.getStatus()).isEqualTo(BookingStatus.PENDING);

        // Clean up
        bookingRepository.deleteAll();

        // 2. CANCELLED booking does not block
        bookingRepository.save(new Booking(
                customer1,
                roomA,
                LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(14),
                1,
                BookingStatus.CANCELLED,
                "Cancelled by customer"
        ));

        Booking b2 = bookingService.createBooking(
                customer2.getEmail(),
                new BookingForm(roomA.getId(), LocalDate.now().plusDays(10), LocalDate.now().plusDays(14), 1)
        );
        assertThat(b2).isNotNull();
        assertThat(b2.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ── 4. Different Room Unaffected ───────────────────────────────────────

    @Test
    @DisplayName("A different room with the same dates is unaffected")
    void differentRoom_withSameDates_isUnaffected() {
        // roomA has booking for days 10 to 14; roomB requested for same days
        Booking booking = bookingService.createBooking(
                customer2.getEmail(),
                new BookingForm(roomB.getId(), LocalDate.now().plusDays(10), LocalDate.now().plusDays(14), 1)
        );

        assertThat(booking).isNotNull();
        assertThat(booking.getRoom().getId()).isEqualTo(roomB.getId());
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    // ── 5. findAvailableRooms ──────────────────────────────────────────────

    @Test
    @DisplayName("findAvailableRooms excludes the booked room and includes the free ones")
    void findAvailableRooms_excludesBookedRoom_andIncludesFreeOnes() {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = LocalDate.now().plusDays(14);

        List<Room> available = roomService.findAvailableRooms(checkIn, checkOut, 1);

        assertThat(available)
                .extracting(Room::getId)
                .contains(roomB.getId())
                .doesNotContain(roomA.getId());
    }

    // ── 6. Concurrency ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Concurrency: two simultaneous requests for the same room and dates result in exactly one booking and one BookingConflictException")
    void concurrentBookings_exactlyOneSucceeds() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = LocalDate.now().plusDays(24);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> otherErrors = Collections.synchronizedList(new ArrayList<>());

        Callable<Void> task1 = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            try {
                bookingService.createBooking(customer1.getEmail(), new BookingForm(roomA.getId(), checkIn, checkOut, 1));
                successCount.incrementAndGet();
            } catch (BookingConflictException ex) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                otherErrors.add(t);
            }
            return null;
        };

        Callable<Void> task2 = () -> {
            readyLatch.countDown();
            readyLatch.await();
            startLatch.await();
            try {
                bookingService.createBooking(customer2.getEmail(), new BookingForm(roomA.getId(), checkIn, checkOut, 1));
                successCount.incrementAndGet();
            } catch (BookingConflictException ex) {
                conflictCount.incrementAndGet();
            } catch (Throwable t) {
                otherErrors.add(t);
            }
            return null;
        };

        Future<Void> f1 = executor.submit(task1);
        Future<Void> f2 = executor.submit(task2);

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // Release both threads at the exact same moment

        f1.get(10, TimeUnit.SECONDS);
        f2.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(otherErrors).as("Expected no unhandled exceptions during concurrency").isEmpty();
        assertThat(successCount.get()).as("Exactly one concurrent booking must succeed").isEqualTo(1);
        assertThat(conflictCount.get()).as("Exactly one concurrent booking must fail with BookingConflictException").isEqualTo(1);

        List<Booking> saved = bookingRepository.findOverlapping(roomA.getId(), checkIn, checkOut, BookingStatus.BLOCKING);
        assertThat(saved).hasSize(1);
    }
}
