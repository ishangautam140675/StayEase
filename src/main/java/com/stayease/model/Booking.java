package com.stayease.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Entity representing a room booking reservation made by a user.
 */
@Entity
@Table(name = "bookings", indexes = {
    @Index(name = "idx_bookings_room_dates", columnList = "room_id, check_in, check_out"),
    @Index(name = "idx_bookings_reference", columnList = "booking_reference"),
    @Index(name = "idx_bookings_idempotency", columnList = "idempotency_key")
})
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", length = 32, unique = true)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "guests", nullable = false)
    private int guests;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BookingStatus status;

    @Column(name = "price_per_night_at_booking", precision = 10, scale = 2)
    private java.math.BigDecimal pricePerNightAtBooking;

    @Column(name = "total_price", precision = 10, scale = 2)
    private java.math.BigDecimal totalPrice;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "admin_remark", length = 500)
    private String adminRemark;

    @Column(name = "operational_notes", length = 1000)
    private String operationalNotes;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    @Column(name = "checked_out_at")
    private LocalDateTime checkedOutAt;

    @jakarta.persistence.OneToMany(mappedBy = "booking", cascade = jakarta.persistence.CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @jakarta.persistence.OrderBy("changedAt ASC")
    private java.util.List<BookingStatusHistory> statusHistory = new java.util.ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public Booking() {
    }

    public Booking(User user, Room room, LocalDate checkIn, LocalDate checkOut, int guests, BookingStatus status, String adminRemark) {
        this.user = user;
        this.room = room;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guests = guests;
        this.status = status;
        this.adminRemark = adminRemark;
        if (room != null && room.getPricePerNight() != null) {
            this.pricePerNightAtBooking = room.getPricePerNight();
            this.totalPrice = this.pricePerNightAtBooking.multiply(java.math.BigDecimal.valueOf(getNights()));
        }
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        if (this.bookingReference == null || this.bookingReference.trim().isEmpty()) {
            this.bookingReference = generateBookingReference(this.createdAt != null ? this.createdAt.toLocalDate() : LocalDate.now());
        }
        if (this.pricePerNightAtBooking == null && this.room != null && this.room.getPricePerNight() != null) {
            this.pricePerNightAtBooking = this.room.getPricePerNight();
        }
        if (this.totalPrice == null && this.pricePerNightAtBooking != null) {
            this.totalPrice = this.pricePerNightAtBooking.multiply(java.math.BigDecimal.valueOf(getNights()));
        }
    }

    public static String generateBookingReference(LocalDate date) {
        String datePart = (date != null ? date : LocalDate.now()).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
        String randomSuffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "SE-" + datePart + "-" + randomSuffix;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public void setCheckIn(LocalDate checkIn) {
        this.checkIn = checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public void setCheckOut(LocalDate checkOut) {
        this.checkOut = checkOut;
    }

    public int getGuests() {
        return guests;
    }

    public void setGuests(int guests) {
        this.guests = guests;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus status) {
        this.status = status;
    }

    public String getAdminRemark() {
        return adminRemark;
    }

    public void setAdminRemark(String adminRemark) {
        this.adminRemark = adminRemark;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public long getNights() {
        if (checkIn == null || checkOut == null) {
            return 0;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public void setBookingReference(String bookingReference) {
        this.bookingReference = bookingReference;
    }

    public java.math.BigDecimal getPricePerNightAtBooking() {
        return pricePerNightAtBooking;
    }

    public void setPricePerNightAtBooking(java.math.BigDecimal pricePerNightAtBooking) {
        this.pricePerNightAtBooking = pricePerNightAtBooking;
    }

    public java.math.BigDecimal getEffectivePricePerNight() {
        if (pricePerNightAtBooking != null) {
            return pricePerNightAtBooking;
        }
        if (room != null && room.getPricePerNight() != null) {
            return room.getPricePerNight();
        }
        return java.math.BigDecimal.ZERO;
    }

    public java.math.BigDecimal getTotalPrice() {
        if (totalPrice != null) {
            return totalPrice;
        }
        if (room == null || room.getPricePerNight() == null) {
            return java.math.BigDecimal.ZERO;
        }
        return room.getPricePerNight().multiply(java.math.BigDecimal.valueOf(getNights()));
    }

    public void setTotalPrice(java.math.BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public java.util.List<BookingStatusHistory> getStatusHistory() {
        return statusHistory;
    }

    public void setStatusHistory(java.util.List<BookingStatusHistory> statusHistory) {
        this.statusHistory = statusHistory;
    }

    public void addStatusHistory(BookingStatus fromStatus, BookingStatus toStatus, String changedBy, String reason) {
        BookingStatusHistory history = new BookingStatusHistory(this, fromStatus, toStatus, changedBy, reason);
        this.statusHistory.add(history);
    }

    public String getOperationalNotes() {
        return operationalNotes;
    }

    public void setOperationalNotes(String operationalNotes) {
        this.operationalNotes = operationalNotes;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public void setCheckedInAt(LocalDateTime checkedInAt) {
        this.checkedInAt = checkedInAt;
    }

    public LocalDateTime getCheckedOutAt() {
        return checkedOutAt;
    }

    public void setCheckedOutAt(LocalDateTime checkedOutAt) {
        this.checkedOutAt = checkedOutAt;
    }

    public com.stayease.dto.PriceBreakdown getPriceBreakdown() {
        return new com.stayease.dto.PriceBreakdown(getNights(), getEffectivePricePerNight(), checkIn, checkOut);
    }

    public boolean isCancellable() {
        return (status == BookingStatus.PENDING || status == BookingStatus.APPROVED)
                && checkIn != null && checkIn.isAfter(LocalDate.now());
    }

    public boolean isCancellable(int leadHours) {
        if (!isCancellable()) {
            return false;
        }
        LocalDateTime deadline = getCancellationDeadline(leadHours);
        return deadline != null && LocalDateTime.now().isBefore(deadline);
    }

    public LocalDateTime getCancellationDeadline(int leadHours) {
        if (checkIn == null) {
            return null;
        }
        return checkIn.atTime(14, 0).minusHours(leadHours);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Booking booking)) return false;
        return id != null && Objects.equals(id, booking.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
