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
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final SeasonalPricingService seasonalPricingService;

    @org.springframework.beans.factory.annotation.Value("${app.booking.cancellation-lead-hours:24}")
    private int cancellationLeadHours = 24;

    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository) {
        this(bookingRepository, roomRepository, userRepository, null, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public BookingService(BookingRepository bookingRepository,
                          RoomRepository roomRepository,
                          UserRepository userRepository,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) EmailService emailService,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) AuditLogService auditLogService,
                          @org.springframework.beans.factory.annotation.Autowired(required = false) SeasonalPricingService seasonalPricingService) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.seasonalPricingService = seasonalPricingService;
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

        // Idempotent creation: if form was submitted with an idempotency key that already succeeded,
        // return the existing booking instead of creating a duplicate or failing.
        if (form.getIdempotencyKey() != null && !form.getIdempotencyKey().trim().isEmpty()) {
            java.util.Optional<Booking> existing = bookingRepository.findByIdempotencyKey(form.getIdempotencyKey().trim());
            if (existing.isPresent()) {
                Booking existingBooking = existing.get();
                if (existingBooking.getUser() != null && existingBooking.getUser().getEmail().equalsIgnoreCase(userEmail)) {
                    return existingBooking;
                }
            }
        }

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

        if (room.isUnderMaintenance()) {
            throw new BookingValidationException("roomId", "Room " + room.getRoomNumber() + " is currently under maintenance and cannot be reserved.");
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

        long nights = calculateNights(form.getCheckIn(), form.getCheckOut());
        BigDecimal pricePerNight = (seasonalPricingService != null)
                ? seasonalPricingService.calculateStayAverageRate(room, form.getCheckIn(), form.getCheckOut())
                : room.getPricePerNight();
        BigDecimal total = calculateTotalPrice(pricePerNight, nights);

        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoom(room);
        booking.setCheckIn(form.getCheckIn());
        booking.setCheckOut(form.getCheckOut());
        booking.setGuests(form.getGuests());
        booking.setStatus(BookingStatus.PENDING);
        booking.setPricePerNightAtBooking(pricePerNight);
        booking.setTotalPrice(total);
        booking.setBookingReference(Booking.generateBookingReference(LocalDate.now()));
        if (form.getIdempotencyKey() != null && !form.getIdempotencyKey().trim().isEmpty()) {
            booking.setIdempotencyKey(form.getIdempotencyKey().trim());
        }
        booking.addStatusHistory(null, BookingStatus.PENDING, user.getEmail(), "Booking requested by guest");

        Booking saved = bookingRepository.save(booking);
        if (emailService != null) {
            emailService.sendBookingCreated(saved);
        }
        return saved;
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
        return approve(bookingId, "ADMIN");
    }

    /**
     * Approves a PENDING booking, recording the approving administrator's identifier.
     */
    @Transactional
    public Booking approve(Long bookingId, String adminEmail) {
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
        booking.addStatusHistory(BookingStatus.PENDING, BookingStatus.APPROVED,
                adminEmail != null ? adminEmail : "ADMIN", "Booking approved by administrator");
        Booking saved = bookingRepository.save(booking);

        if (auditLogService != null) {
            auditLogService.log(
                    adminEmail,
                    "BOOKING_APPROVED",
                    "BOOKING",
                    String.valueOf(saved.getId()),
                    "Approved booking #" + saved.getId() + " (" + saved.getBookingReference() + ") for Room " +
                    (saved.getRoom() != null ? saved.getRoom().getRoomNumber() : "")
            );
        }

        if (emailService != null) {
            emailService.sendBookingApproved(saved);
        }
        return saved;
    }

    /**
     * Checks in a guest for an APPROVED booking.
     * Transitions status from APPROVED -> CHECKED_IN.
     */
    @Transactional
    public Booking checkIn(Long bookingId, String adminEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Booking #" + bookingId + " cannot be checked in because its status is " + booking.getStatus() +
                    " (only APPROVED bookings can be checked in)."
            );
        }

        booking.setStatus(BookingStatus.CHECKED_IN);
        booking.setCheckedInAt(java.time.LocalDateTime.now());
        booking.addStatusHistory(BookingStatus.APPROVED, BookingStatus.CHECKED_IN,
                adminEmail != null ? adminEmail : "ADMIN", "Guest checked in");
        Booking saved = bookingRepository.save(booking);

        if (auditLogService != null) {
            auditLogService.log(
                    adminEmail,
                    "GUEST_CHECKED_IN",
                    "BOOKING",
                    String.valueOf(saved.getId()),
                    "Checked in guest for booking #" + saved.getId() + " (" + saved.getBookingReference() + ")"
            );
        }

        return saved;
    }

    /**
     * Checks out a guest for a CHECKED_IN booking.
     * Transitions status from CHECKED_IN -> CHECKED_OUT. Releases inventory block.
     */
    @Transactional
    public Booking checkOut(Long bookingId, String adminEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.CHECKED_IN) {
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Booking #" + bookingId + " cannot be checked out because its status is " + booking.getStatus() +
                    " (only CHECKED_IN bookings can be checked out)."
            );
        }

        booking.setStatus(BookingStatus.CHECKED_OUT);
        booking.setCheckedOutAt(java.time.LocalDateTime.now());
        booking.addStatusHistory(BookingStatus.CHECKED_IN, BookingStatus.CHECKED_OUT,
                adminEmail != null ? adminEmail : "ADMIN", "Guest checked out");
        Booking saved = bookingRepository.save(booking);

        if (auditLogService != null) {
            auditLogService.log(
                    adminEmail,
                    "GUEST_CHECKED_OUT",
                    "BOOKING",
                    String.valueOf(saved.getId()),
                    "Checked out guest for booking #" + saved.getId() + " (" + saved.getBookingReference() + ")"
            );
        }

        return saved;
    }

    /**
     * Marks an APPROVED booking as NO_SHOW if the guest did not arrive.
     * Transitions status from APPROVED -> NO_SHOW. Releases inventory block.
     */
    @Transactional
    public Booking markNoShow(Long bookingId, String adminEmail, String remark) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        if (booking.getStatus() != BookingStatus.APPROVED) {
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Booking #" + bookingId + " cannot be marked as NO_SHOW because its status is " + booking.getStatus() +
                    " (only APPROVED bookings can be marked as NO_SHOW)."
            );
        }

        String reason = (remark != null && !remark.trim().isEmpty()) ? remark.trim() : "Guest marked as no-show";
        booking.setStatus(BookingStatus.NO_SHOW);
        booking.setAdminRemark(reason);
        booking.addStatusHistory(BookingStatus.APPROVED, BookingStatus.NO_SHOW,
                adminEmail != null ? adminEmail : "ADMIN", reason);
        Booking saved = bookingRepository.save(booking);

        if (auditLogService != null) {
            auditLogService.log(
                    adminEmail,
                    "BOOKING_NO_SHOW",
                    "BOOKING",
                    String.valueOf(saved.getId()),
                    "Marked booking #" + saved.getId() + " (" + saved.getBookingReference() + ") as NO_SHOW. Reason: " + reason
            );
        }

        return saved;
    }

    /**
     * Updates internal operational notes for a booking.
     */
    @Transactional
    public Booking updateOperationalNotes(Long bookingId, String notes, String adminEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        String trimmed = (notes != null) ? notes.trim() : null;
        if (trimmed != null && trimmed.length() > 1000) {
            trimmed = trimmed.substring(0, 1000);
        }
        booking.setOperationalNotes(trimmed);
        Booking saved = bookingRepository.save(booking);

        if (auditLogService != null) {
            auditLogService.log(
                    adminEmail,
                    "OPERATIONAL_NOTES_UPDATED",
                    "BOOKING",
                    String.valueOf(saved.getId()),
                    "Updated operational notes for booking #" + saved.getId()
            );
        }

        return saved;
    }

    /**
     * Searches administrative bookings with status, keyword (customer name, email, ref, room),
     * and date-range filters.
     */
    public Page<Booking> searchAdminBookings(BookingStatus status, String query, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String cleanQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        return bookingRepository.searchAdminBookings(status, cleanQuery, startDate, endDate, pageable);
    }

    /**
     * Retrieves bookings for CSV export with optional status and date filters.
     */
    public List<Booking> listForExport(BookingStatus status, LocalDate startDate, LocalDate endDate) {
        return bookingRepository.findBookingsForExport(status, startDate, endDate);
    }

    /**
     * Rejects a PENDING booking with a mandatory admin remark (max 500 characters).
     * The booking must exist and be PENDING (otherwise InvalidBookingStateException).
     */
    @Transactional
    public Booking reject(Long bookingId, String remark) {
        return reject(bookingId, remark, "ADMIN");
    }

    /**
     * Rejects a PENDING booking with a mandatory admin remark, recording administrator email.
     */
    @Transactional
    public Booking reject(Long bookingId, String remark, String adminEmail) {
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
        booking.addStatusHistory(BookingStatus.PENDING, BookingStatus.REJECTED,
                adminEmail != null ? adminEmail : "ADMIN", trimmedRemark);
        Booking saved = bookingRepository.save(booking);

        if (auditLogService != null) {
            auditLogService.log(
                    adminEmail,
                    "BOOKING_REJECTED",
                    "BOOKING",
                    String.valueOf(saved.getId()),
                    "Rejected booking #" + saved.getId() + " (" + saved.getBookingReference() + "). Reason: " + trimmedRemark
            );
        }

        if (emailService != null) {
            emailService.sendBookingRejected(saved, trimmedRemark);
        }
        return saved;
    }

    /**
     * Cancels a booking by the customer who owns it (Phase 12, extended in Phase 3 with policy check).
     *
     * @param bookingId the booking ID
     * @param userEmail the authenticated user's email
     * @return the cancelled booking
     * @throws com.stayease.exception.BookingNotFoundException if the booking does not exist
     * @throws org.springframework.security.access.AccessDeniedException if the booking belongs to someone else
     * @throws com.stayease.exception.InvalidBookingStateException if status is not PENDING/APPROVED or cancellation window closed
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

        if (!booking.isCancellable(cancellationLeadHours)) {
            java.time.LocalDateTime deadline = booking.getCancellationDeadline(cancellationLeadHours);
            String deadlineStr = deadline != null
                    ? deadline.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a"))
                    : "the check-in date";
            throw new com.stayease.exception.InvalidBookingStateException(
                    "Booking #" + bookingId + " cannot be cancelled because the cancellation deadline (" +
                    deadlineStr + ") has passed (cancellation policy: free cancellation up to " +
                    cancellationLeadHours + " hours before check-in)."
            );
        }

        BookingStatus oldStatus = booking.getStatus();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.addStatusHistory(oldStatus, BookingStatus.CANCELLED, userEmail, "Cancelled by guest");
        Booking saved = bookingRepository.save(booking);
        if (emailService != null) {
            emailService.sendBookingCancelled(saved);
        }
        return saved;
    }

    /**
     * Retrieves paginated bookings for a customer filtered by status and optional search term.
     */
    public Page<Booking> getBookingsForUser(String userEmail, BookingStatus status, String query, Pageable pageable) {
        String trimmedQuery = (query != null && !query.trim().isEmpty()) ? query.trim() : null;
        return bookingRepository.searchUserBookings(userEmail, status, trimmedQuery, pageable);
    }

    /**
     * Retrieves a booking by ID with eagerly loaded status history and related entities.
     * Verifies that the requester owns the booking (unless userEmail is null, e.g. for admin access).
     */
    public Booking getBookingWithDetails(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findByIdWithDetails(bookingId)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with id: " + bookingId));

        if (userEmail != null && (booking.getUser() == null || !booking.getUser().getEmail().equalsIgnoreCase(userEmail))) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to view this booking.");
        }
        return booking;
    }

    /**
     * Retrieves a booking by its public reference code with eagerly loaded status history and related entities.
     * Verifies that the requester owns the booking (unless userEmail is null, e.g. for admin access).
     */
    public Booking getBookingByReferenceWithDetails(String bookingReference, String userEmail) {
        Booking booking = bookingRepository.findByBookingReferenceWithDetails(bookingReference)
                .orElseThrow(() -> new com.stayease.exception.BookingNotFoundException("Booking not found with reference: " + bookingReference));

        if (userEmail != null && (booking.getUser() == null || !booking.getUser().getEmail().equalsIgnoreCase(userEmail))) {
            throw new org.springframework.security.access.AccessDeniedException("You do not have permission to view this booking.");
        }
        return booking;
    }

    /**
     * Helper calculating an itemized price breakdown for a room and date range.
     */
    public com.stayease.dto.PriceBreakdown calculatePriceBreakdown(Room room, LocalDate checkIn, LocalDate checkOut) {
        if (room == null || room.getPricePerNight() == null) {
            return new com.stayease.dto.PriceBreakdown(0, BigDecimal.ZERO, checkIn, checkOut);
        }
        long nights = calculateNights(checkIn, checkOut);
        BigDecimal effectiveRate = (seasonalPricingService != null && checkIn != null && checkOut != null && checkIn.isBefore(checkOut))
                ? seasonalPricingService.calculateStayAverageRate(room, checkIn, checkOut)
                : room.getPricePerNight();
        return new com.stayease.dto.PriceBreakdown(nights, effectiveRate, checkIn, checkOut);
    }

    public int getCancellationLeadHours() {
        return cancellationLeadHours;
    }

    public void setCancellationLeadHours(int cancellationLeadHours) {
        this.cancellationLeadHours = cancellationLeadHours;
    }
}
