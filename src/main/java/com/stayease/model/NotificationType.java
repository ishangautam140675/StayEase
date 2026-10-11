package com.stayease.model;

/**
 * Types of asynchronous and transactional notifications sent to customers.
 */
public enum NotificationType {
    BOOKING_REQUESTED,
    BOOKING_APPROVED,
    BOOKING_REJECTED,
    BOOKING_CANCELLED,
    BOOKING_REMINDER,
    PASSWORD_RESET,
    EMAIL_VERIFICATION
}
