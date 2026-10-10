package com.stayease.exception;

/**
 * Thrown when an administrative action (such as approve or reject) is attempted on a booking
 * that is not in the required state (e.g. attempting to approve a non-PENDING booking).
 */
public class InvalidBookingStateException extends RuntimeException {

    public InvalidBookingStateException(String message) {
        super(message);
    }
}
