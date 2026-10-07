package com.ticketing.booking.exception;

/** Maps to 404. */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public static NotFoundException event(Long id) {
        return new NotFoundException("No event with id " + id);
    }

    public static NotFoundException hold(String reference) {
        return new NotFoundException("No hold with reference " + reference);
    }

    public static NotFoundException booking(String reference) {
        return new NotFoundException("No booking with reference " + reference);
    }
}
