package com.stayease.repository;

import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Role;
import com.stayease.model.Room;
import com.stayease.model.RoomType;
import com.stayease.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class RepositoryTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    @DisplayName("save a User and find it by email; existsByEmail is true")
    void saveUserAndFindByEmail() {
        User user = new User("John Doe", "john@example.com", "hashedPassword123", "9876543210", Role.CUSTOMER);
        userRepository.save(user);

        Optional<User> found = userRepository.findByEmail("john@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("John Doe");
        assertThat(found.get().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(found.get().getCreatedAt()).isNotNull();

        assertThat(userRepository.existsByEmail("john@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nonexistent@example.com")).isFalse();
    }

    @Test
    @DisplayName("saving two users with the same email fails")
    void savingTwoUsersWithSameEmailFails() {
        User user1 = new User("Alice Smith", "duplicate@example.com", "hash1", "1234567890", Role.CUSTOMER);
        userRepository.saveAndFlush(user1);

        User user2 = new User("Bob Jones", "duplicate@example.com", "hash2", "0987654321", Role.ADMIN);
        assertThrows(DataIntegrityViolationException.class, () -> {
            userRepository.saveAndFlush(user2);
        });
    }

    @Test
    @DisplayName("saving two rooms with the same roomNumber fails")
    void savingTwoRoomsWithSameRoomNumberFails() {
        Room room1 = new Room("101", RoomType.SINGLE, 1, new BigDecimal("1200.00"), "Standard Single", true);
        roomRepository.saveAndFlush(room1);

        Room room2 = new Room("101", RoomType.DOUBLE, 2, new BigDecimal("1800.00"), "Deluxe Double", true);
        assertThrows(DataIntegrityViolationException.class, () -> {
            roomRepository.saveAndFlush(room2);
        });
    }

    @Test
    @DisplayName("save a Room, a User and a Booking, then read the booking back with its status and dates")
    void saveAndReadBookingWithStatusAndDates() {
        User user = new User("Alice Guest", "alice.guest@example.com", "guestPass123", "5551234567", Role.CUSTOMER);
        User savedUser = userRepository.save(user);

        Room room = new Room("202", RoomType.DOUBLE, 2, new BigDecimal("2500.50"), "Comfort Double with AC", true);
        Room savedRoom = roomRepository.save(room);

        LocalDate checkIn = LocalDate.of(2026, 11, 10);
        LocalDate checkOut = LocalDate.of(2026, 11, 15);

        Booking booking = new Booking(savedUser, savedRoom, checkIn, checkOut, 2, BookingStatus.PENDING, "Late check-in requested");
        Booking savedBooking = bookingRepository.save(booking);

        assertThat(savedBooking.getId()).isNotNull();
        assertThat(savedBooking.getCreatedAt()).isNotNull();

        Optional<Booking> retrieved = bookingRepository.findById(savedBooking.getId());
        assertThat(retrieved).isPresent();
        Booking foundBooking = retrieved.get();
        assertThat(foundBooking.getStatus()).isEqualTo(BookingStatus.PENDING);
        assertThat(foundBooking.getCheckIn()).isEqualTo(checkIn);
        assertThat(foundBooking.getCheckOut()).isEqualTo(checkOut);
        assertThat(foundBooking.getGuests()).isEqualTo(2);
        assertThat(foundBooking.getAdminRemark()).isEqualTo("Late check-in requested");

        List<Booking> userBookings = bookingRepository.findByUserOrderByCreatedAtDesc(savedUser);
        assertThat(userBookings).hasSize(1);
        assertThat(userBookings.get(0).getId()).isEqualTo(savedBooking.getId());

        assertThat(bookingRepository.existsByRoomId(savedRoom.getId())).isTrue();
        assertThat(bookingRepository.existsByRoomId(999999L)).isFalse();
    }

    @Test
    @DisplayName("BookingStatus.isBlocking() is true for PENDING, APPROVED, and CHECKED_IN; false for CHECKED_OUT, NO_SHOW, REJECTED, CANCELLED")
    void bookingStatusIsBlocking() {
        assertThat(BookingStatus.PENDING.isBlocking()).isTrue();
        assertThat(BookingStatus.APPROVED.isBlocking()).isTrue();
        assertThat(BookingStatus.CHECKED_IN.isBlocking()).isTrue();
        assertThat(BookingStatus.CHECKED_OUT.isBlocking()).isFalse();
        assertThat(BookingStatus.NO_SHOW.isBlocking()).isFalse();
        assertThat(BookingStatus.REJECTED.isBlocking()).isFalse();
        assertThat(BookingStatus.CANCELLED.isBlocking()).isFalse();

        assertThat(BookingStatus.BLOCKING)
                .containsExactlyInAnyOrder(BookingStatus.PENDING, BookingStatus.APPROVED, BookingStatus.CHECKED_IN);
    }
}
