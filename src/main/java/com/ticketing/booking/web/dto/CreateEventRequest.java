package com.ticketing.booking.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Seats are described as a layout rather than listed one by one, so creating a
 * 2000-seat venue is a small request body.
 */
public record CreateEventRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 200) String venue,
        @NotNull @Future Instant startsAt,
        @NotEmpty @Valid List<SectionLayout> sections) {

    public record SectionLayout(
            @NotBlank @Size(max = 50) String name,
            @Min(1) int rows,
            @Min(1) int seatsPerRow,
            @NotNull @Positive BigDecimal price) {
    }
}
