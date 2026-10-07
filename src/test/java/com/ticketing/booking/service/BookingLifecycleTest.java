package com.ticketing.booking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.ticketing.booking.domain.HoldStatus;
import com.ticketing.booking.domain.Seat;
import com.ticketing.booking.domain.SeatStatus;
import com.ticketing.booking.exception.HoldNotUsableException;
import com.ticketing.booking.exception.InvalidRequestException;
import com.ticketing.booking.exception.NotFoundException;
import com.ticketing.booking.exception.SeatsUnavailableException;
import com.ticketing.booking.repository.SeatRepository;
import com.ticketing.booking.support.MutableClock;
import com.ticketing.booking.web.dto.BookingResponse;
import com.ticketing.booking.web.dto.CreateEventRequest;
import com.ticketing.booking.web.dto.CreateEventRequest.SectionLayout;
import com.ticketing.booking.web.dto.CreateHoldRequest;
import com.ticketing.booking.web.dto.HoldResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.convention.TestBean;

/** End-to-end behaviour of the hold / confirm / expire lifecycle. */
@SpringBootTest(properties = "booking.hold-duration=PT10M")
@ActiveProfiles("test")
class BookingLifecycleTest {

    @Autowired
    private EventService eventService;

    @Autowired
    private HoldService holdService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Replaces the application's system clock with one this test drives. The
     * static factory below supplies the replacement; Spring wires it in place of
     * the real bean for the lifetime of the class.
     */
    @TestBean(name = "clock")
    private Clock clock;

    private static final MutableClock TEST_CLOCK = new MutableClock();

    static Clock clock() {
        return TEST_CLOCK;
    }

    private MutableClock testClock;
    private Long eventId;
    private List<Long> seatIds;

    @BeforeEach
    void setUp() {
        testClock = TEST_CLOCK;
        testClock.reset(Instant.parse("2026-06-01T10:00:00Z"));

        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY FALSE");
        for (String table : List.of("seats", "seat_holds", "bookings", "events")) {
            jdbcTemplate.execute("TRUNCATE TABLE " + table);
        }
        jdbcTemplate.execute("SET REFERENTIAL_INTEGRITY TRUE");

        eventId = eventService
                .createEvent(new CreateEventRequest(
                        "Lifecycle Show",
                        "Test Hall",
                        Instant.now().plus(20, ChronoUnit.DAYS),
                        List.of(new SectionLayout("Stalls", 2, 4, new BigDecimal("30.00")))))
                .id();

        seatIds = seatRepository.findByEventIdOrderById(eventId, PageRequest.of(0, 20)).getContent().stream()
                .map(Seat::getId)
                .toList();
    }

    @Test
    @DisplayName("Hold then confirm produces a booking priced from the held seats")
    void holdThenConfirmCreatesBooking() {
        HoldResponse hold = holdService.createHold(
                eventId, new CreateHoldRequest("buyer@example.com", List.of(seatIds.get(0), seatIds.get(1))));

        assertThat(hold.status()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.totalPrice()).isEqualByComparingTo("60.00");
        assertThat(hold.expiresAt()).isEqualTo(Instant.parse("2026-06-01T10:10:00Z"));
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.HELD)).isEqualTo(2);

        BookingResponse booking = bookingService.confirm(hold.holdReference());

        assertThat(booking.bookingReference()).hasSize(8);
        assertThat(booking.totalPrice()).isEqualByComparingTo("60.00");
        assertThat(booking.seats()).hasSize(2);
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.BOOKED)).isEqualTo(2);
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.HELD)).isZero();
        assertThat(holdService.findByReference(hold.holdReference()).status()).isEqualTo(HoldStatus.CONFIRMED);
    }

    @Test
    @DisplayName("A hold cannot be confirmed twice")
    void confirmingTwiceIsRejected() {
        HoldResponse hold =
                holdService.createHold(eventId, new CreateHoldRequest("buyer@example.com", List.of(seatIds.get(0))));
        bookingService.confirm(hold.holdReference());

        assertThatExceptionOfType(HoldNotUsableException.class)
                .isThrownBy(() -> bookingService.confirm(hold.holdReference()))
                .withMessageContaining("already been confirmed");
    }

    @Test
    @DisplayName("An expired hold is refused even before the sweeper runs")
    void expiredHoldCannotBeConfirmed() {
        HoldResponse hold =
                holdService.createHold(eventId, new CreateHoldRequest("slow@example.com", List.of(seatIds.get(0))));

        // Past the 10-minute window, but the sweeper has deliberately not run.
        testClock.advance(Duration.ofMinutes(11));

        assertThatExceptionOfType(HoldNotUsableException.class)
                .isThrownBy(() -> bookingService.confirm(hold.holdReference()))
                .withMessageContaining("expired");
    }

    @Test
    @DisplayName("The sweeper returns expired holds to the available pool")
    void sweeperReleasesExpiredHolds() {
        holdService.createHold(
                eventId, new CreateHoldRequest("abandoner@example.com", List.of(seatIds.get(0), seatIds.get(1))));
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE)).isEqualTo(6);

        testClock.advance(Duration.ofMinutes(11));
        int expired = holdService.expireStaleHolds();

        assertThat(expired).isEqualTo(1);
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE)).isEqualTo(8);
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.HELD)).isZero();
    }

    @Test
    @DisplayName("The sweeper leaves holds that are still inside their window")
    void sweeperIgnoresLiveHolds() {
        holdService.createHold(eventId, new CreateHoldRequest("prompt@example.com", List.of(seatIds.get(0))));

        testClock.advance(Duration.ofMinutes(5));

        assertThat(holdService.expireStaleHolds()).isZero();
        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.HELD)).isEqualTo(1);
    }

    @Test
    @DisplayName("Releasing a hold frees the seats immediately")
    void releaseFreesSeats() {
        HoldResponse hold =
                holdService.createHold(eventId, new CreateHoldRequest("mindchanger@example.com", List.of(seatIds.get(2))));

        holdService.release(hold.holdReference());

        assertThat(seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE)).isEqualTo(8);
        assertThat(holdService.findByReference(hold.holdReference()).status()).isEqualTo(HoldStatus.RELEASED);
    }

    @Test
    @DisplayName("Released seats can be held by someone else")
    void releasedSeatsBecomeBookableAgain() {
        Long seat = seatIds.get(3);
        HoldResponse first = holdService.createHold(eventId, new CreateHoldRequest("first@example.com", List.of(seat)));
        holdService.release(first.holdReference());

        HoldResponse second = holdService.createHold(eventId, new CreateHoldRequest("second@example.com", List.of(seat)));

        assertThat(second.seats()).singleElement().extracting("id").isEqualTo(seat);
    }

    @Test
    @DisplayName("Holding an already-held seat reports which seat is gone")
    void heldSeatIsReportedAsUnavailable() {
        Long seat = seatIds.get(0);
        holdService.createHold(eventId, new CreateHoldRequest("first@example.com", List.of(seat)));

        assertThatExceptionOfType(SeatsUnavailableException.class)
                .isThrownBy(() -> holdService.createHold(
                        eventId, new CreateHoldRequest("second@example.com", List.of(seat))))
                .satisfies(exception -> assertThat(exception.getUnavailableSeats()).containsExactly("Stalls-A-1"));
    }

    @Test
    @DisplayName("Duplicate seat ids in one request are rejected")
    void duplicateSeatIdsRejected() {
        Long seat = seatIds.get(0);

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> holdService.createHold(
                        eventId, new CreateHoldRequest("sneaky@example.com", List.of(seat, seat))))
                .withMessageContaining("duplicates");
    }

    @Test
    @DisplayName("Seats must belong to the event in the path")
    void seatsFromAnotherEventRejected() {
        Long otherEventId = eventService
                .createEvent(new CreateEventRequest(
                        "Other Show",
                        "Other Hall",
                        Instant.now().plus(25, ChronoUnit.DAYS),
                        List.of(new SectionLayout("A", 1, 2, new BigDecimal("10.00")))))
                .id();

        assertThatExceptionOfType(InvalidRequestException.class)
                .isThrownBy(() -> holdService.createHold(
                        otherEventId, new CreateHoldRequest("confused@example.com", List.of(seatIds.get(0)))))
                .withMessageContaining("must belong to event");
    }

    @Test
    @DisplayName("Unknown references surface as not-found")
    void unknownReferencesAreNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> holdService.findByReference("no-such-hold"));
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> bookingService.findByReference("NOSUCH12"));
        assertThatExceptionOfType(NotFoundException.class).isThrownBy(() -> eventService.findById(999_999L));
    }

    @Test
    @DisplayName("Event detail reports a live seat-status breakdown")
    void eventDetailCountsSeatsByStatus() {
        HoldResponse hold = holdService.createHold(
                eventId, new CreateHoldRequest("counter@example.com", List.of(seatIds.get(0), seatIds.get(1))));
        bookingService.confirm(hold.holdReference());
        holdService.createHold(eventId, new CreateHoldRequest("counter2@example.com", List.of(seatIds.get(2))));

        var detail = eventService.findById(eventId);

        assertThat(detail.totalSeats()).isEqualTo(8);
        assertThat(detail.bookedSeats()).isEqualTo(2);
        assertThat(detail.heldSeats()).isEqualTo(1);
        assertThat(detail.availableSeats()).isEqualTo(5);
    }

    @Test
    @DisplayName("Customer bookings are listed newest first")
    void customerBookingsAreListed() {
        HoldResponse hold =
                holdService.createHold(eventId, new CreateHoldRequest("repeat@example.com", List.of(seatIds.get(0))));
        bookingService.confirm(hold.holdReference());

        var page = bookingService.findByCustomer("REPEAT@example.com", PageRequest.of(0, 10));

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).singleElement().extracting(BookingResponse::customerEmail)
                .isEqualTo("repeat@example.com");
    }
}
