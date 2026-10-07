package com.ticketing.booking.web;

import com.ticketing.booking.domain.SeatStatus;
import com.ticketing.booking.service.EventService;
import com.ticketing.booking.web.dto.CreateEventRequest;
import com.ticketing.booking.web.dto.EventDetailResponse;
import com.ticketing.booking.web.dto.EventSummaryResponse;
import com.ticketing.booking.web.dto.PageResponse;
import com.ticketing.booking.web.dto.SeatResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
@Tag(name = "Events", description = "Event catalogue and seat inventory")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @Operation(summary = "Create an event and generate its seat inventory")
    public ResponseEntity<EventDetailResponse> create(@Valid @RequestBody CreateEventRequest request) {
        EventDetailResponse created = eventService.createEvent(request);
        return ResponseEntity.created(URI.create("/api/v1/events/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List events, optionally filtered by venue and date window")
    public PageResponse<EventSummaryResponse> list(
            @RequestParam(required = false) String venue,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return eventService.search(venue, from, to, pageable);
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Event detail with a live seat-status breakdown")
    public EventDetailResponse get(@PathVariable Long eventId) {
        return eventService.findById(eventId);
    }

    @GetMapping("/{eventId}/seats")
    @Operation(summary = "Seats for an event, optionally filtered by status")
    public PageResponse<SeatResponse> seats(
            @PathVariable Long eventId,
            @RequestParam(required = false) SeatStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        return eventService.findSeats(eventId, status, pageable);
    }
}
