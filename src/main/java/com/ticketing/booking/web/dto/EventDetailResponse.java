package com.ticketing.booking.web.dto;

import com.ticketing.booking.domain.Event;
import java.time.Instant;

public record EventDetailResponse(
        Long id,
        String name,
        String venue,
        Instant startsAt,
        long totalSeats,
        long availableSeats,
        long heldSeats,
        long bookedSeats,
        Instant createdAt) {

    public static EventDetailResponse from(
            Event event, long totalSeats, long availableSeats, long heldSeats, long bookedSeats) {
        return new EventDetailResponse(
                event.getId(),
                event.getName(),
                event.getVenue(),
                event.getStartsAt(),
                totalSeats,
                availableSeats,
                heldSeats,
                bookedSeats,
                event.getCreatedAt());
    }
}
