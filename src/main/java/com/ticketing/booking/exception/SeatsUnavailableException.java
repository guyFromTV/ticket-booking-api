package com.ticketing.booking.exception;

import java.util.List;

/**
 * Maps to 409. Carries the seats that were already taken so the client can show
 * the user exactly which ones to re-pick rather than a bare failure.
 */
public class SeatsUnavailableException extends RuntimeException {

    private final List<String> unavailableSeats;

    public SeatsUnavailableException(List<String> unavailableSeats) {
        super("Seats no longer available: " + String.join(", ", unavailableSeats));
        this.unavailableSeats = List.copyOf(unavailableSeats);
    }

    public List<String> getUnavailableSeats() {
        return unavailableSeats;
    }
}
