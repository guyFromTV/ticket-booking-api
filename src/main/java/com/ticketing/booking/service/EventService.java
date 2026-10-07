package com.ticketing.booking.service;

import com.ticketing.booking.domain.Event;
import com.ticketing.booking.domain.Seat;
import com.ticketing.booking.domain.SeatStatus;
import com.ticketing.booking.exception.NotFoundException;
import com.ticketing.booking.repository.EventRepository;
import com.ticketing.booking.repository.SeatRepository;
import com.ticketing.booking.web.dto.CreateEventRequest;
import com.ticketing.booking.web.dto.EventDetailResponse;
import com.ticketing.booking.web.dto.EventSummaryResponse;
import com.ticketing.booking.web.dto.PageResponse;
import com.ticketing.booking.web.dto.SeatResponse;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final SeatRepository seatRepository;

    public EventService(EventRepository eventRepository, SeatRepository seatRepository) {
        this.eventRepository = eventRepository;
        this.seatRepository = seatRepository;
    }

    /** Creates the event and its entire seat inventory in one transaction. */
    @Transactional
    public EventDetailResponse createEvent(CreateEventRequest request) {
        Event event = new Event(request.name(), request.venue(), request.startsAt());

        for (CreateEventRequest.SectionLayout section : request.sections()) {
            for (int row = 0; row < section.rows(); row++) {
                String rowLabel = rowLabel(row);
                for (int number = 1; number <= section.seatsPerRow(); number++) {
                    event.addSeat(new Seat(section.name(), rowLabel, number, section.price()));
                }
            }
        }

        Event saved = eventRepository.save(event);
        long total = saved.getSeats().size();
        return EventDetailResponse.from(saved, total, total, 0, 0);
    }

    @Transactional(readOnly = true)
    public PageResponse<EventSummaryResponse> search(String venue, Instant from, Instant to, Pageable pageable) {
        Page<Event> page = eventRepository.search(venue, from, to, pageable);
        return PageResponse.of(page, EventSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public EventDetailResponse findById(Long eventId) {
        Event event = eventRepository.findById(eventId).orElseThrow(() -> NotFoundException.event(eventId));
        return EventDetailResponse.from(
                event,
                seatRepository.countByEventId(eventId),
                seatRepository.countByEventIdAndStatus(eventId, SeatStatus.AVAILABLE),
                seatRepository.countByEventIdAndStatus(eventId, SeatStatus.HELD),
                seatRepository.countByEventIdAndStatus(eventId, SeatStatus.BOOKED));
    }

    @Transactional(readOnly = true)
    public PageResponse<SeatResponse> findSeats(Long eventId, SeatStatus status, Pageable pageable) {
        if (!eventRepository.existsById(eventId)) {
            throw NotFoundException.event(eventId);
        }
        Page<Seat> page = status == null
                ? seatRepository.findByEventIdOrderById(eventId, pageable)
                : seatRepository.findByEventIdAndStatusOrderById(eventId, status, pageable);
        return PageResponse.of(page, SeatResponse::from);
    }

    /** A, B, ... Z, AA, AB, ... so a section can exceed 26 rows. */
    private static String rowLabel(int index) {
        StringBuilder label = new StringBuilder();
        int remaining = index;
        do {
            label.insert(0, (char) ('A' + remaining % 26));
            remaining = remaining / 26 - 1;
        } while (remaining >= 0);
        return label.toString();
    }
}
