package com.ticketing.booking.web.dto;

import com.ticketing.booking.domain.HoldStatus;
import com.ticketing.booking.domain.SeatHold;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record HoldResponse(
        String holdReference,
        Long eventId,
        String customerEmail,
        HoldStatus status,
        Instant expiresAt,
        BigDecimal totalPrice,
        List<SeatResponse> seats) {

    public static HoldResponse from(SeatHold hold) {
        return new HoldResponse(
                hold.getHoldReference(),
                hold.getEvent().getId(),
                hold.getCustomerEmail(),
                hold.getStatus(),
                hold.getExpiresAt(),
                hold.totalPrice(),
                hold.getSeats().stream().map(SeatResponse::from).toList());
    }
}
