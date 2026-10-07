package com.ticketing.booking.exception;

/** Maps to 400 for rule violations that Bean Validation cannot express. */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
