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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
     * Retrieves paginated booking history for a specific customer.
     */
    public Page<Booking> getBookingsForUser(String userEmail, Pageable pageable) {
        return bookingRepository.findByUserEmailOrderByCreatedAtDesc(userEmail, pageable);
    }

    // ── Admin Management Methods (Phase 11) ────────────────────────────────

    /**
     * Lists bookings for the administrative console. If status is specified,
     * filters by that status; otherwise returns all bookings, newest first.
     * Both customer and room associations are eagerly loaded.
     */
    public List<Booking> listForAdmin(BookingStatus status) {
        if (status == null) {
            return bookingRepository.findAllWithUserAndRoomOrderByCreatedAtDesc();
        }
        return bookingRepository.findAllWithUserAndRoomByStatusOrderByCreatedAtDesc(status);
    }

    /**
     * Lists paginated bookings for the administrative console with optional status filter.
     */
    public Page<Booking> listForAdmin(BookingStatus status, Pageable pageable) {
        if (status == null) {
            return bookingRepository.findAllWithUserAndRoomOrderByCreatedAtDesc(pageable);
        }
        return bookingRepository.findAllWithUserAndRoomByStatusOrderByCreatedAtDesc(status, pageable);
    }

    /**
     * Counts bookings by status for the administrative filter tabs.
     * Includes total count under key "ALL".
     */
    public java.util.Map<String, Long> countByStatus() {
        java.util.Map<String, Long> counts = new java.util.HashMap<>();
        counts.put("ALL", bookingRepository.count());
        for (BookingStatus s : BookingStatus.values()) {
            counts.put(s.name(), bookingRepository.countByStatus(s));
        }
        return counts;
    }

    /**
     * Approves a PENDING booking.
     * The booking must exist and be PENDING (otherwise InvalidBookingStateException).
     * Locks the room row with findByIdForUpdate, re-checks overlap with the
     * "exclude this booking" query, throws BookingConflictException if conflicts exist,
     * and sets status to APPROVED.
     */
    @Transactional
    public Booking approve(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Booking #" + bookingId + " cannot be approved because its status is " + booking.getStatus() +
                    " (only PENDING bookings can be approved)."
            );
        }

        // Lock room row to serialize concurrent approvals/bookings
        roomRepository.findByIdForUpdate(booking.getRoom().getId());

        List<Booking> conflicts = bookingRepository.findOverlapping(
                booking.getRoom().getId(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                BookingStatus.BLOCKING,
                booking.getId()
        );

        if (!conflicts.isEmpty()) {
            throw new BookingConflictException(
                    "Cannot approve booking #" + bookingId + " due to date conflicts with another booking.",
                    conflicts
            );
        }

        booking.setStatus(BookingStatus.APPROVED);
        return bookingRepository.save(booking);
    }

    /**
     * Rejects a PENDING booking with a mandatory admin remark (max 500 characters).
     * The booking must exist and be PENDING (otherwise InvalidBookingStateException).
     */
    @Transactional
    public Booking reject(Long bookingId, String remark) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Booking #" + bookingId + " cannot be rejected because its status is " + booking.getStatus() +
                    " (only PENDING bookings can be rejected)."
            );
        }

        if (remark == null || remark.trim().isEmpty()) {
            throw new IllegalArgumentException("Admin remark is required when rejecting a booking.");
        }

        String trimmedRemark = remark.trim();
        if (trimmedRemark.length() > 500) {
            throw new IllegalArgumentException("Admin remark cannot exceed 500 characters.");
        }

        booking.setStatus(BookingStatus.REJECTED);
        booking.setAdminRemark(trimmedRemark);
        return bookingRepository.save(booking);
    }

    /**
     * Cancels a booking by the customer who owns it (Phase 12).
     *
     * @param bookingId the booking ID
     * @param userEmail the authenticated user's email
     * @return the cancelled booking
     * @throws com.stayease.exception.BookingNotFoundException if the booking does not exist
     * @throws org.springframework.security.access.AccessDeniedException if the booking belongs to someone else
     * @throws com.stayease.exception.InvalidBookingStateException if status is not PENDING/APPROVED or check-in is not after today
     */
    @Transactional
    public Booking cancel(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getUser() == null || !booking.getUser().getEmail().equalsIgnoreCase(userEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to cancel this booking.");
        }

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.APPROVED) {
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Booking #" + bookingId + " cannot be cancelled because its status is " + booking.getStatus() +
                    " (only PENDING or APPROVED bookings can be cancelled)."
            );
        }

        if (booking.getCheckIn() == null || !booking.getCheckIn().isAfter(LocalDate.now())) {
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Bookings can only be cancelled before the check-in date."
            );
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }
}
