package com.ticketing.booking.exception;

/** Maps to 409: the hold exists but has expired, been released, or been confirmed already. */
public class HoldNotUsableException extends RuntimeException {

    public HoldNotUsableException(String message) {
        super(message);
    }
}
