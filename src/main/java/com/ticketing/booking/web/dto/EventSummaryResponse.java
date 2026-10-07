package com.ticketing.booking.web.dto;

import com.ticketing.booking.domain.Event;
import java.time.Instant;

public record EventSummaryResponse(Long id, String name, String venue, Instant startsAt) {

    public static EventSummaryResponse from(Event event) {
        return new EventSummaryResponse(event.getId(), event.getName(), event.getVenue(), event.getStartsAt());
    }
}
