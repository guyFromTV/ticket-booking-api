package com.ticketing.booking.service;

import com.ticketing.booking.config.BookingProperties;
import com.ticketing.booking.domain.Event;
import com.ticketing.booking.domain.HoldStatus;
import com.ticketing.booking.domain.Seat;
import com.ticketing.booking.domain.SeatHold;
import com.ticketing.booking.exception.InvalidRequestException;
import com.ticketing.booking.exception.NotFoundException;
import com.ticketing.booking.exception.SeatsUnavailableException;
import com.ticketing.booking.repository.EventRepository;
import com.ticketing.booking.repository.SeatHoldRepository;
import com.ticketing.booking.repository.SeatRepository;
import com.ticketing.booking.web.dto.CreateHoldRequest;
import com.ticketing.booking.web.dto.HoldResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates and releases seat holds.
 *
 * <p>This is the only writer that contends on seat rows, so it is the only place
 * that has to think about concurrency.
 */
@Service
public class HoldService {

    private static final Logger log = LoggerFactory.getLogger(HoldService.class);

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;
    private final SeatHoldRepository holdRepository;
    private final BookingProperties properties;
    private final Clock clock;

    public HoldService(
            EventRepository eventRepository,
            SeatRepository seatRepository,
            SeatHoldRepository holdRepository,
            BookingProperties properties,
            Clock clock) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
        this.holdRepository = holdRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Holds the requested seats for this customer.
     *
     * <p>The ordering of steps is the whole point:
     * <ol>
     *   <li>lock the seat rows ({@code SELECT ... FOR UPDATE}) -- a competing
     *       transaction asking for any of the same seats now waits here;</li>
     *   <li>only then check availability, reading state no one else can change;</li>
     *   <li>write the hold and commit, releasing the lock.</li>
     * </ol>
     * Checking before locking would be a classic time-of-check-to-time-of-use bug:
     * both requests would see "available" and both would succeed.
     *
     * @throws SeatsUnavailableException if any requested seat is already held or sold
     */
    @Transactional
    public HoldResponse createHold(Long eventId, CreateHoldRequest request) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> NotFoundException.event(eventId));

        // Duplicates in the request would otherwise inflate the price and claim
        // one seat twice; reject rather than silently de-duplicate.
        Set<Long> requestedIds = Set.copyOf(request.seatIds());
        if (requestedIds.size() != request.seatIds().size()) {
            throw new InvalidRequestException("seatIds contains duplicates");
        }

        List<Seat> seats = seatRepository.lockAllById(requestedIds);

        if (seats.size() != requestedIds.size()) {
            throw new NotFoundException("One or more seats do not exist");
        }
        if (seats.stream().anyMatch(seat -> !seat.getEvent().getId().equals(eventId))) {
            throw new InvalidRequestException("All seats must belong to event " + eventId);
        }

        List<String> taken = seats.stream().filter(seat -> !seat.isAvailable()).map(Seat::label).sorted().toList();
        if (!taken.isEmpty()) {
            throw new SeatsUnavailableException(taken);
        }

        Instant now = clock.instant();
        SeatHold hold = new SeatHold(event, request.customerEmail(), now.plus(properties.holdDuration()));
        seats.forEach(hold::claim);

        SeatHold saved = holdRepository.save(hold);
        log.info("Held {} seat(s) for {} under {}", seats.size(), request.customerEmail(), saved.getHoldReference());
        return HoldResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public HoldResponse findByReference(String holdReference) {
        return HoldResponse.from(requireHold(holdReference));
    }

    /** Gives the seats back early. Idempotent for an already-finished hold. */
    @Transactional
    public void release(String holdReference) {
        SeatHold hold = requireHold(holdReference);
        if (hold.getStatus() != HoldStatus.ACTIVE) {
            log.debug("Hold {} already in terminal state {}", holdReference, hold.getStatus());
            return;
        }
        hold.release(HoldStatus.RELEASED);
        log.info("Released hold {}", holdReference);
    }

    /**
     * Returns seats from holds that ran out of time. Runs in its own transaction,
     * driven by the scheduler.
     */
    @Transactional
    public int expireStaleHolds() {
        Instant now = clock.instant();
        List<SeatHold> stale = holdRepository.findByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, now);
        stale.forEach(hold -> hold.release(HoldStatus.EXPIRED));
        if (!stale.isEmpty()) {
            log.info("Expired {} stale hold(s)", stale.size());
        }
        return stale.size();
    }

    private SeatHold requireHold(String holdReference) {
        return holdRepository
                .findByHoldReference(holdReference)
                .orElseThrow(() -> NotFoundException.hold(holdReference));
    }
}
