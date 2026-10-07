package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.ticketing.booking.domain.Seat;
import com.ticketing.booking.domain.SeatStatus;
import com.ticketing.booking.exception.SeatsUnavailableException;
import com.ticketing.booking.repository.SeatHoldRepository;
import com.ticketing.booking.repository.SeatRepository;
import com.ticketing.booking.web.dto.CreateEventRequest;
import com.ticketing.booking.web.dto.CreateEventRequest.SectionLayout;
import com.ticketing.booking.web.dto.CreateHoldRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * The test that justifies the locking strategy.
 *
 * <p>A seat sold twice is the defining bug of a booking system, and it only shows
 * up under genuine parallelism -- a sequential test passes whether or not the
 * locking is correct. Each task here runs on its own thread, so each gets its own
 * transaction and connection, and they race for the same rows.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentHoldTest {

    private static final int THREADS = 50;

    @Autowired
    private EventService eventService;

    @Autowired
    private HoldService holdService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatHoldRepository holdRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long eventId;
    private List<Long> seatIds;

    @BeforeEach
    void setUp() {
        // Seats point at holds and bookings, so ordinary deleteAll() would trip the
        // foreign keys. Dropping referential integrity for the wipe keeps the
        // fixture independent of table order.
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of("seats", "seat_holds", "bookings", "events")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

        eventId = eventService
                .createEvent(new CreateEventRequest(
                        "Race Night",
                        "Test Arena",
                        Instant.now().plus(10, ChronoUnit.DAYS),
                        List.of(new SectionLayout("A", 1, 10, new BigDecimal("50.00")))))
                .id();

        seatIds = seatRepository.findByEventIdOrderById(eventId, org.springframework.data.domain.PageRequest.of(0, 50))
                .getContent()
                .stream()
                .map(Seat::getId)
                .toList();
        assertThat(seatIds).hasSize(10);
    }

    @Test
    @DisplayName("50 threads fighting for one seat: exactly one hold wins")
    void onlyOneRequestWinsTheSameSeat() throws Exception {
        Long contestedSeat = seatIds.getFirst();

        Outcomes outcomes = raceFor(THREADS, index -> List.of(contestedSeat));

        assertThat(outcomes.succeeded).as("exactly one hold must win the seat").isEqualTo(1);
        assertThat(outcomes.rejected).as("everyone else must be told the seat is gone").isEqualTo(THREADS - 1);
        assertThat(outcomes.unexpected).as("no unexpected failures: %s", outcomes.errors).isEqualTo(0);

        assertThat(holdRepository.count()).isEqualTo(1);
        assertThat(seatRepository.findById(contestedSeat))
                .get()
                .extracting(Seat::getStatus)
                .isEqualTo(SeatStatus.HELD);
    }

    @Test
    @DisplayName("Overlapping seat sets requested in opposite orders do not deadlock")
    void overlappingRequestsInOppositeOrdersDoNotDeadlock() throws Exception {
        Long first = seatIds.get(0);
        Long second = seatIds.get(1);

        // Half ask for [first, second], half for [second, first]. Because the
        // locking query sorts by id, every transaction still takes the rows in the
        // same sequence, so these cannot form a lock cycle.
        Outcomes outcomes = raceFor(20, index -> index % 2 == 0 ? List.of(first, second) : List.of(second, first));

        assertThat(outcomes.succeeded).isEqualTo(1);
        assertThat(outcomes.rejected).isEqualTo(19);
        assertThat(outcomes.unexpected).as("a deadlock would surface here: %s", outcomes.errors).isEqualTo(0);
    }

    @Test
    @DisplayName("Requests for different seats all succeed in parallel")
    void disjointRequestsAllSucceed() throws Exception {
        // Guards against over-locking: serialising correctness must not turn
        // independent bookings into conflicts.
        Outcomes outcomes = raceFor(seatIds.size(), index -> List.of(seatIds.get(index)));

        assertThat(outcomes.succeeded).isEqualTo(seatIds.size());
        assertThat(outcomes.rejected).isEqualTo(0);
        assertThat(outcomes.unexpected).as("errors: %s", outcomes.errors).isEqualTo(0);
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.HELD)).isEqualTo(seatIds.size());
    }

    /** Fires {@code count} concurrent hold attempts and tallies how each one ended. */
    private Outcomes raceFor(int count, java.util.function.IntFunction<List<Long>> seatsForTask) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<Result>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    // Line every thread up so they hit the lock together rather
                    // than trickling in as they are scheduled.
                    startGun.await();
                    try {
                        holdService.createHold(
                                eventId,
                                new CreateHoldRequest("racer" + index + "@example.com", seatsForTask.apply(index)));
                        return new Result(Kind.SUCCEEDED, null);
                    } catch (SeatsUnavailableException expected) {
                        return new Result(Kind.REJECTED, null);
                    } catch (RuntimeException unexpected) {
                        return new Result(Kind.UNEXPECTED, unexpected.getClass().getSimpleName() + ": "
                                + unexpected.getMessage());
                    }
                }));
            }

            startGun.countDown();
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS))
                    .as("all attempts should finish; a hang here means a deadlock")
                    .isTrue();

            Outcomes outcomes = new Outcomes();
            for (Future<Result> future : futures) {
                Result result = future.get();
                switch (result.kind) {
                    case SUCCEEDED -> outcomes.succeeded++;
                    case REJECTED -> outcomes.rejected++;
                    case UNEXPECTED -> {
                        outcomes.unexpected++;
                        outcomes.errors.add(result.detail);
                    }
                }
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }

    private enum Kind {
        SUCCEEDED,
        REJECTED,
        UNEXPECTED
    }

    private record Result(Kind kind, String detail) {
    }

    private static final class Outcomes {
        private int succeeded;
        private int rejected;
        private int unexpected;
        private final List<String> errors = new ArrayList<>();
    }
}
