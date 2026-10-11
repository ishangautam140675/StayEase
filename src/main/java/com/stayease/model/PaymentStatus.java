package com.stayease.model;

/**
 * Represents the lifecycle status of a payment for a booking.
 * Decoupled from {@link BookingStatus}.
 */
public enum PaymentStatus {
    UNPAID,
    PENDING,
    SUCCESSFUL,
    FAILED,
    REFUNDED,
    PARTIALLY_REFUNDED;

    /**
     * Checks if the payment is in a completed/settled state.
     */
    public boolean isSettled() {
        return this == SUCCESSFUL;
    }

    /**
     * Checks if the payment is eligible for refund.
     */
    public boolean isRefundable() {
        return this == SUCCESSFUL;
    }
}
