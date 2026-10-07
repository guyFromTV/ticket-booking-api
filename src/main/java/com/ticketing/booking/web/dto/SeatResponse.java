package com.ticketing.booking.web.dto;

import com.ticketing.booking.domain.Seat;
import com.ticketing.booking.domain.SeatStatus;
import java.math.BigDecimal;

public record SeatResponse(
        Long id, String section, String rowLabel, int seatNumber, BigDecimal price, SeatStatus status) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getSection(),
                seat.getRowLabel(),
                seat.getSeatNumber(),
                seat.getPrice(),
                seat.getStatus());
    }
}
