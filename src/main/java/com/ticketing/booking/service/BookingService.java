package com.ticketing.booking.service;

import com.ticketing.booking.domain.Booking;
import com.ticketing.booking.domain.Seat;
import com.ticketing.booking.domain.SeatHold;
import com.ticketing.booking.exception.HoldNotUsableException;
import com.ticketing.booking.exception.NotFoundException;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.repository.SeatHoldRepository;
import com.ticketing.booking.repository.SeatRepository;
import com.ticketing.booking.web.dto.BookingResponse;
import com.ticketing.booking.web.dto.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Turns a hold into a sale. */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final SeatHoldRepository holdRepository;
    private final SeatRepository seatRepository;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            SeatHoldRepository holdRepository,
            SeatRepository seatRepository,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.holdRepository = holdRepository;
        this.seatRepository = seatRepository;
        this.clock = clock;
    }

    /**
     * Confirms a hold into a booking.
     *
     * <p>Expiry is judged against the clock, not against the stored status: a hold
     * whose window has passed is refused even if the sweeper has not yet got to
     * it. Without that check there would be a window in which an expired hold
     * still converts.
     *
     * @throws HoldNotUsableException if the hold expired, was released, or was already confirmed
     */
    @Transactional
    public BookingResponse confirm(String holdReference) {
        SeatHold hold = holdRepository
                .findByHoldReference(holdReference)
                .orElseThrow(() -> NotFoundException.hold(holdReference));

        Instant now = clock.instant();
        if (!hold.isUsableAt(now)) {
            throw new HoldNotUsableException(switch (hold.getStatus()) {
                case ACTIVE -> "Hold " + holdReference + " expired at " + hold.getExpiresAt();
                case CONFIRMED -> "Hold " + holdReference + " has already been confirmed";
                case RELEASED -> "Hold " + holdReference + " was released";
                case EXPIRED -> "Hold " + holdReference + " expired";
            });
        }

        // Re-lock the seats so the rows cannot shift under the confirmation.
        List<Long> seatIds = hold.getSeats().stream().map(Seat::getId).toList();
        List<Seat> seats = seatRepository.lockAllById(seatIds);

        Booking booking = new Booking(hold.getEvent(), hold.getCustomerEmail());
        seats.forEach(booking::addSeat);
        hold.markConfirmed();

        Booking saved = bookingRepository.save(booking);
        log.info("Confirmed hold {} as booking {} ({})", holdReference, saved.getBookingReference(), saved.getTotalPrice());
        return BookingResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse findByReference(String bookingReference) {
        return bookingRepository
                .findByBookingReference(bookingReference)
                .map(BookingResponse::from)
                .orElseThrow(() -> NotFoundException.booking(bookingReference));
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> findByCustomer(String customerEmail, Pageable pageable) {
        Page<Booking> page = bookingRepository.findByCustomerEmailIgnoreCase(customerEmail, pageable);
        return PageResponse.of(page, BookingResponse::from);
    }
}
