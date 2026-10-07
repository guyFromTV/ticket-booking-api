package com.ticketing.booking.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateHoldRequest(
        @NotBlank @Email String customerEmail,
        @NotEmpty @Size(max = 10, message = "a single hold may cover at most 10 seats")
        List<Long> seatIds) {
}
