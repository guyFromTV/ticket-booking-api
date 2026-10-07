package com.ticketing.booking.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised booking rules. Typed configuration beats scattered
 * {@code @Value} strings: the duration is parsed and validated at startup.
 *
 * @param holdDuration how long a new hold stays usable
 */
@ConfigurationProperties(prefix = "booking")
public record BookingProperties(Duration holdDuration) {

    public BookingProperties {
        if (holdDuration == null || holdDuration.isZero() || holdDuration.isNegative()) {
            throw new IllegalArgumentException("booking.hold-duration must be a positive duration");
        }
    }
}
