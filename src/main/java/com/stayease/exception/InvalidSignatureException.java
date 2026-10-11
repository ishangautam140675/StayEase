package com.stayease.exception;

/**
 * Thrown when a webhook or verification signature check fails.
 */
public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String message) {
        super(message);
    }
}
