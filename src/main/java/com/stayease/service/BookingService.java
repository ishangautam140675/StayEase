package com.stayease.service;

import com.stayease.dto.BookingForm;
import com.stayease.exception.BookingConflictException;
import com.stayease.exception.BookingValidationException;
import com.stayease.model.Booking;
import com.stayease.model.BookingStatus;
import com.stayease.model.Room;
import com.stayease.model.User;
import com.stayease.repository.BookingRepository;
import com.stayease.repository.RoomRepository;
import com.stayease.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Service handling customer room booking reservations and booking history.
 */
@Service
@Transactional(readOnly = true)
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates a new booking reservation for the authenticated user.
     * The user is resolved from the logged-in session, never from the form.
     *
     * @param userEmail email of the authenticated customer
     * @param form      form containing booking details
     * @return the saved {@link Booking} with status {@link BookingStatus#PENDING}
     * @throws BookingValidationException if any business validation rule is violated
     * @throws BookingConflictException   if dates overlap with an existing PENDING or APPROVED booking
     */
    @Transactional
    public Booking createBooking(String userEmail, BookingForm form) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));

        if (form.getRoomId() == null) {
            throw new BookingValidationException("roomId", "Room selection is required.");
        }

        // Lock the room row with PESSIMISTIC_WRITE (SELECT ... FOR UPDATE).
        // This serializes concurrent booking requests for the same room, preventing race conditions
        // where multiple simultaneous transactions check for overlapping dates before either has committed.
        Room room = roomRepository.findByIdForUpdate(form.getRoomId())
                .orElseThrow(() -> new BookingValidationException("roomId", "The selected room does not exist."));

        if (!room.isActive()) {
            throw new BookingValidationException("roomId", "The selected room is inactive and cannot be booked.");
        }

        LocalDate today = LocalDate.now();
        if (form.getCheckIn() == null) {
            throw new BookingValidationException("checkIn", "Check-in date is required.");
        }
        if (form.getCheckIn().isBefore(today)) {
            throw new BookingValidationException("checkIn", "Check-in date cannot be in the past.");
        }

        if (form.getCheckOut() == null) {
            throw new BookingValidationException("checkOut", "Check-out date is required.");
        }
        if (!form.getCheckOut().isAfter(form.getCheckIn())) {
            throw new BookingValidationException("checkOut", "Check-out date must be after the check-in date (minimum 1 night stay).");
        }

        if (form.getGuests() == null || form.getGuests() < 1) {
            throw new BookingValidationException("guests", "Number of guests must be at least 1.");
        }
        if (form.getGuests() > room.getCapacity()) {
            throw new BookingValidationException("guests",
                    "Number of guests (" + form.getGuests() + ") exceeds the room capacity of " + room.getCapacity() + ".");
        }

        // Overlap detection: only PENDING or APPROVED bookings actively block room inventory
        List<Booking> conflicts = bookingRepository.findOverlapping(
                room.getId(),
                form.getCheckIn(),
                form.getCheckOut(),
                BookingStatus.BLOCKING
        );

        if (!conflicts.isEmpty()) {
            throw new BookingConflictException(conflicts);
        }

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoom(room);
        booking.setCheckIn(form.getCheckIn());
        booking.setCheckOut(form.getCheckOut());
        booking.setGuests(form.getGuests());
        booking.setStatus(BookingStatus.PENDING);

        return bookingRepository.save(booking);
    }

    /**
     * Calculates the number of nights between check-in and check-out dates.
     */
    public long calculateNights(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null || checkOut == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /**
     * Calculates total price given a nightly price and number of nights.
     */
    public BigDecimal calculateTotalPrice(BigDecimal pricePerNight, long nights) {
        if (pricePerNight == null || nights <= 0) {
            return BigDecimal.ZERO;
        }
        return pricePerNight.multiply(BigDecimal.valueOf(nights));
    }

    /**
     * Helper returning calculated nights and total price for a room and dates.
     */
    public BigDecimal calculateTotalPrice(Room room, LocalDate checkIn, LocalDate checkOut) {
        if (room == null || room.getPricePerNight() == null) {
            return BigDecimal.ZERO;
        }
        long nights = calculateNights(checkIn, checkOut);
        return calculateTotalPrice(room.getPricePerNight(), nights);
    }

    /**
     * Retrieves all bookings for a user ordered by creation date descending.
     */
    public List<Booking> getBookingsForUser(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + userEmail));
        return bookingRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * Alias for {@link #getBookingsForUser(String)}.
     */
    public List<Booking> findCustomerBookings(String userEmail) {
        return getBookingsForUser(userEmail);
    }
}
