package com.stayease.exception;

/**
 * Thrown when attempting to create or update a room with a room number that already exists.
 */
public class DuplicateRoomNumberException extends RuntimeException {

    public DuplicateRoomNumberException(String message) {
        super(message);
    }
}
