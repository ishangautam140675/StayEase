package com.stayease.exception;

import com.stayease.model.Booking;

import java.util.Collections;
import java.util.List;

/**
 * Exception thrown when a booking reservation request conflicts with existing
 * overlapping bookings (status PENDING or APPROVED) for the specified room.
 * Carries the list of conflicting bookings and their dates.
 */
public class BookingConflictException extends RuntimeException {

    private final List<Booking> conflictingBookings;

    public BookingConflictException(List<Booking> conflictingBookings) {
        super("The requested dates conflict with existing bookings for this room.");
        this.conflictingBookings = conflictingBookings != null
                ? Collections.unmodifiableList(conflictingBookings)
                : Collections.emptyList();
    }

    public BookingConflictException(String message, List<Booking> conflictingBookings) {
        super(message);
        this.conflictingBookings = conflictingBookings != null
                ? Collections.unmodifiableList(conflictingBookings)
                : Collections.emptyList();
    }

    public List<Booking> getConflictingBookings() {
        return conflictingBookings;
    }
}
