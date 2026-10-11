package com.stayease.exception;

/**
 * Thrown when payment parameters or transaction state validation fails.
 */
public class PaymentValidationException extends RuntimeException {

    public PaymentValidationException(String message) {
        super(message);
    }

    public PaymentValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
