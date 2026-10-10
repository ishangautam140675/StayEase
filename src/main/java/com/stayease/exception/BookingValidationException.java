package com.stayease.exception;

/**
 * Exception thrown when a booking reservation request fails domain or business validation rules.
 * Carries the specific form field name and a clear user-facing error message.
 */
public class BookingValidationException extends RuntimeException {

    private final String field;

    public BookingValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
