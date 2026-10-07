package com.ticketing.booking.domain;

public enum HoldStatus {
    /** Seats are reserved and the hold has not passed its expiry instant. */
    ACTIVE,
    /** Turned into a booking. */
    CONFIRMED,
    /** Given up by the client before expiry. */
    RELEASED,
    /** Timed out and swept back into the available pool. */
    EXPIRED
}
