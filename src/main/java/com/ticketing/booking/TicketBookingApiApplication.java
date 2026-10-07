package com.ticketing.booking;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TicketBookingApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TicketBookingApiApplication.class, args);
    }

    /**
     * Time is injected rather than read from {@code Instant.now()} inside the
     * services. That is what lets the expiry tests move the clock forward
     * instead of sleeping for ten minutes.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
