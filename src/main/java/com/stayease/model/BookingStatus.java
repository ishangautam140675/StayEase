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
    CHECKED_IN,
    CHECKED_OUT,
    NO_SHOW,
    REJECTED,
    CANCELLED;

    /**
     * Set of booking statuses that actively block room inventory from other bookings.
     * Both PENDING, APPROVED, and active in-house stays (CHECKED_IN) occupy inventory.
     */
    public static final Set<BookingStatus> BLOCKING = Collections.unmodifiableSet(
            EnumSet.of(PENDING, APPROVED, CHECKED_IN)
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
