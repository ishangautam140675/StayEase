package com.stayease.exception;

/**
 * Thrown by {@link com.stayease.service.AuthService} when a registration
 * attempt uses an email address that already exists in the database.
 */
public class EmailAlreadyUsedException extends RuntimeException {

    public EmailAlreadyUsedException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
