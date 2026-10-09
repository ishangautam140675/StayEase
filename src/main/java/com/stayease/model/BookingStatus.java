package com.stayease.model;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Represents the current status of a room booking.
 */
public enum BookingStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    /**
     * Set of booking statuses that actively block room inventory from other bookings.
     */
    public static final Set<BookingStatus> BLOCKING = Collections.unmodifiableSet(
            EnumSet.of(PENDING, APPROVED)
    );

    /**
     * Checks whether this booking status blocks room availability.
     *
     * @return true only for PENDING and APPROVED
     */
    public boolean isBlocking() {
        return BLOCKING.contains(this);
    }
}
