package com.stayease.service;

import com.stayease.dto.BookingForm;
import com.stayease.exception.BookingValidationException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class BookingServiceTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private UserRepository userRepository;

    private User testCustomer;
    private Room activeRoom;
    private Room inactiveRoom;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.findByEmail("customer.bookings@stayease.com").ifPresent(userRepository::delete);

        testCustomer = userRepository.save(new User(
                "Booking Customer",
                "customer.bookings@stayease.com",
                "EncodedPassword123",
                "1234567890",
                Role.CUSTOMER
        ));

        activeRoom = roomRepository.save(new Room(
                "B101",
                RoomType.DOUBLE,
                2,
                new BigDecimal("800.00"),
                "Active test double room",
                true
        ));

        inactiveRoom = roomRepository.save(new Room(
                "B102",
                RoomType.SINGLE,
                1,
                new BigDecimal("500.00"),
                "Inactive room",
                false
        ));
    }

    @Test
    @DisplayName("A valid booking request is saved as PENDING for the right user")
    void createBooking_validRequest_savedAsPendingForRightUser() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = LocalDate.now().plusDays(4);

        BookingForm form = new BookingForm(activeRoom.getId(), checkIn, checkOut, 2);

        Booking created = bookingService.createBooking(testCustomer.getEmail(), form);

        assertThat(created).isNotNull();
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(created.getUser().getId()).isEqualTo(testCustomer.getId());
        assertThat(created.getUser().getEmail()).isEqualTo(testCustomer.getEmail());
        assertThat(created.getRoom().getId()).isEqualTo(activeRoom.getId());
        assertThat(created.getCheckIn()).isEqualTo(checkIn);
        assertThat(created.getCheckOut()).isEqualTo(checkOut);
        assertThat(created.getGuests()).isEqualTo(2);

        // Verify retrieval in customer history
        List<Booking> userBookings = bookingService.getBookingsForUser(testCustomer.getEmail());
        assertThat(userBookings).hasSize(1);
        assertThat(userBookings.get(0).getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("checkOut equal to or before checkIn is rejected")
    void createBooking_checkOutEqualOrBeforeCheckIn_rejected() {
        LocalDate today = LocalDate.now();

        // 1. checkOut == checkIn
        BookingForm equalDatesForm = new BookingForm(activeRoom.getId(), today.plusDays(2), today.plusDays(2), 1);
        assertThatThrownBy(() -> bookingService.createBooking(testCustomer.getEmail(), equalDatesForm))
                .isInstanceOf(BookingValidationException.class)
                .satisfies(ex -> {
                    BookingValidationException bve = (BookingValidationException) ex;
                    assertThat(bve.getField()).isEqualTo("checkOut");
                    assertThat(bve.getMessage()).containsIgnoringCase("after");
                });

        // 2. checkOut < checkIn
        BookingForm beforeDatesForm = new BookingForm(activeRoom.getId(), today.plusDays(3), today.plusDays(2), 1);
        assertThatThrownBy(() -> bookingService.createBooking(testCustomer.getEmail(), beforeDatesForm))
                .isInstanceOf(BookingValidationException.class)
                .satisfies(ex -> {
                    BookingValidationException bve = (BookingValidationException) ex;
                    assertThat(bve.getField()).isEqualTo("checkOut");
                });
    }

    @Test
    @DisplayName("A past checkIn date is rejected")
    void createBooking_pastCheckIn_rejected() {
        LocalDate pastDate = LocalDate.now().minusDays(1);
        LocalDate futureDate = LocalDate.now().plusDays(2);

        BookingForm pastForm = new BookingForm(activeRoom.getId(), pastDate, futureDate, 1);

        assertThatThrownBy(() -> bookingService.createBooking(testCustomer.getEmail(), pastForm))
                .isInstanceOf(BookingValidationException.class)
                .satisfies(ex -> {
                    BookingValidationException bve = (BookingValidationException) ex;
                    assertThat(bve.getField()).isEqualTo("checkIn");
                    assertThat(bve.getMessage()).containsIgnoringCase("past");
                });
    }

    @Test
    @DisplayName("Guests above room capacity is rejected")
    void createBooking_guestsAboveCapacity_rejected() {
        // activeRoom capacity is 2; request with 3 guests
        BookingForm overCapacityForm = new BookingForm(
                activeRoom.getId(),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(2),
                3
        );

        assertThatThrownBy(() -> bookingService.createBooking(testCustomer.getEmail(), overCapacityForm))
                .isInstanceOf(BookingValidationException.class)
                .satisfies(ex -> {
                    BookingValidationException bve = (BookingValidationException) ex;
                    assertThat(bve.getField()).isEqualTo("guests");
                    assertThat(bve.getMessage()).containsIgnoringCase("capacity");
                });
    }

    @Test
    @DisplayName("An inactive room is rejected")
    void createBooking_inactiveRoom_rejected() {
        BookingForm inactiveRoomForm = new BookingForm(
                inactiveRoom.getId(),
                LocalDate.now().plusDays(1),
                LocalDate.now().plusDays(3),
                1
        );

        assertThatThrownBy(() -> bookingService.createBooking(testCustomer.getEmail(), inactiveRoomForm))
                .isInstanceOf(BookingValidationException.class)
                .satisfies(ex -> {
                    BookingValidationException bve = (BookingValidationException) ex;
                    assertThat(bve.getField()).isEqualTo("roomId");
                    assertThat(bve.getMessage()).containsIgnoringCase("inactive");
                });
    }

    @Test
    @DisplayName("Nights and total price are calculated correctly")
    void calculateNightsAndTotalPrice_correct() {
        LocalDate checkIn = LocalDate.of(2026, 11, 1);
        LocalDate checkOut = LocalDate.of(2026, 11, 5); // 4 nights
        BigDecimal rate = new BigDecimal("850.00");

        long nights = bookingService.calculateNights(checkIn, checkOut);
        assertThat(nights).isEqualTo(4L);

        BigDecimal totalPrice = bookingService.calculateTotalPrice(rate, nights);
        assertThat(totalPrice).isEqualByComparingTo(new BigDecimal("3400.00"));

        // Helper on Room & Dates
        BigDecimal roomTotalPrice = bookingService.calculateTotalPrice(activeRoom, checkIn, checkOut);
        assertThat(roomTotalPrice).isEqualByComparingTo(new BigDecimal("3200.00")); // 4 * 800.00

        // Entity helper methods
        Booking booking = new Booking();
        booking.setRoom(activeRoom);
        booking.setCheckIn(checkIn);
        booking.setCheckOut(checkOut);

        assertThat(booking.getNights()).isEqualTo(4L);
        assertThat(booking.getTotalPrice()).isEqualByComparingTo(new BigDecimal("3200.00"));
    }
}
