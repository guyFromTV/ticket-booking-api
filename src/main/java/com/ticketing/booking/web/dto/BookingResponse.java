package com.ticketing.booking.web.dto;

import com.ticketing.booking.domain.Booking;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record BookingResponse(
        String bookingReference,
        Long eventId,
        String eventName,
        String customerEmail,
        BigDecimal totalPrice,
        List<SeatResponse> seats,
        Instant confirmedAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getBookingReference(),
                booking.getEvent().getId(),
                booking.getEvent().getName(),
                booking.getCustomerEmail(),
                booking.getTotalPrice(),
                booking.getSeats().stream().map(SeatResponse::from).toList(),
                booking.getCreatedAt());
    }
}
