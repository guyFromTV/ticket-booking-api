package com.ticketing.booking.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/**
 * A clock the tests can move by hand, so expiry behaviour is verified without
 * sleeping for the real hold duration.
 */
public class MutableClock extends Clock {

    private final ZoneId zone;
    private Instant instant;

    public MutableClock(Instant start, ZoneId zone) {
        this.instant = start;
        this.zone = zone;
    }

    public MutableClock() {
        this(Instant.parse("2026-01-01T12:00:00Z"), ZoneId.of("UTC"));
    }

    public void advance(Duration amount) {
        this.instant = this.instant.plus(amount);
    }

    public void reset(Instant to) {
        this.instant = to;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        return new MutableClock(instant, newZone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
