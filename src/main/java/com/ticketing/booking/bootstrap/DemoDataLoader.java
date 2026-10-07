package com.ticketing.booking.bootstrap;

import com.ticketing.booking.service.EventService;
import com.ticketing.booking.web.dto.CreateEventRequest;
import com.ticketing.booking.web.dto.CreateEventRequest.SectionLayout;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Seeds a couple of events so the API is explorable the moment it starts. Not
 * active under the {@code test} profile, which would otherwise pollute test data.
 */
@Component
@Profile("!test")
public class DemoDataLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

    private final EventService eventService;

    public DemoDataLoader(EventService eventService) {
        this.eventService = eventService;
    }

    @Override
    public void run(String... args) {
        Instant base = Instant.now().truncatedTo(ChronoUnit.HOURS);

        eventService.createEvent(new CreateEventRequest(
                "Mugham Night",
                "Heydar Aliyev Palace",
                base.plus(30, ChronoUnit.DAYS),
                List.of(
                        new SectionLayout("Stalls", 5, 12, new BigDecimal("75.00")),
                        new SectionLayout("Balcony", 3, 10, new BigDecimal("40.00")))));

        eventService.createEvent(new CreateEventRequest(
                "Qarabag vs Neftchi",
                "Tofiq Bahramov Stadium",
                base.plus(14, ChronoUnit.DAYS),
                List.of(new SectionLayout("North", 10, 20, new BigDecimal("25.00")))));

        log.info("Seeded demo events. Try: curl http://localhost:8080/api/v1/events");
    }
}
