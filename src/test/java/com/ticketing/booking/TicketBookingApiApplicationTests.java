package com.ticketing.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.booking.scheduler.HoldExpirySweeper;
import com.ticketing.booking.web.BookingController;
import com.ticketing.booking.web.EventController;
import com.ticketing.booking.web.HoldController;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Smoke test: the whole context starts and the key beans are wired. */
@SpringBootTest
@ActiveProfiles("test")
class TicketBookingApiApplicationTests {

    @Autowired
    private EventController eventController;

    @Autowired
    private HoldController holdController;

    @Autowired
    private BookingController bookingController;

    @Autowired
    private HoldExpirySweeper sweeper;

    @Autowired
    private Clock clock;

    @Test
    @DisplayName("Application context loads with every layer present")
    void contextLoads() {
        assertThat(eventController).isNotNull();
        assertThat(holdController).isNotNull();
        assertThat(bookingController).isNotNull();
        assertThat(sweeper).isNotNull();
        assertThat(clock).isNotNull();
    }
}
