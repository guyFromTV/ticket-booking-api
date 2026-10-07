package com.ticketing.booking.web;

import com.ticketing.booking.service.HoldService;
import com.ticketing.booking.web.dto.CreateHoldRequest;
import com.ticketing.booking.web.dto.HoldResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Holds", description = "Time-limited seat reservations")
public class HoldController {

    private final HoldService holdService;

    public HoldController(HoldService holdService) {
        this.holdService = holdService;
    }

    @PostMapping("/api/v1/events/{eventId}/holds")
    @Operation(summary = "Hold seats for an event", description = "409 if any requested seat is already taken")
    public ResponseEntity<HoldResponse> create(
            @PathVariable Long eventId, @Valid @RequestBody CreateHoldRequest request) {
        HoldResponse hold = holdService.createHold(eventId, request);
        return ResponseEntity.created(URI.create("/api/v1/holds/" + hold.holdReference())).body(hold);
    }

    @GetMapping("/api/v1/holds/{holdReference}")
    @Operation(summary = "Inspect a hold")
    public HoldResponse get(@PathVariable String holdReference) {
        return holdService.findByReference(holdReference);
    }

    @DeleteMapping("/api/v1/holds/{holdReference}")
    @Operation(summary = "Release a hold before it expires")
    public ResponseEntity<Void> release(@PathVariable String holdReference) {
        holdService.release(holdReference);
        return ResponseEntity.noContent().build();
    }
}
