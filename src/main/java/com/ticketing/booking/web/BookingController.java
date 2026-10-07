package com.ticketing.booking.web;

import com.ticketing.booking.service.BookingService;
import com.ticketing.booking.web.dto.BookingResponse;
import com.ticketing.booking.web.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Bookings", description = "Confirmed sales")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/api/v1/holds/{holdReference}/confirm")
    @Operation(summary = "Confirm a hold into a booking", description = "409 if the hold expired or was already used")
    public ResponseEntity<BookingResponse> confirm(@PathVariable String holdReference) {
        BookingResponse booking = bookingService.confirm(holdReference);
        return ResponseEntity.created(URI.create("/api/v1/bookings/" + booking.bookingReference()))
                .body(booking);
    }

    @GetMapping("/api/v1/bookings/{bookingReference}")
    @Operation(summary = "Look up a booking by reference")
    public BookingResponse get(@PathVariable String bookingReference) {
        return bookingService.findByReference(bookingReference);
    }

    @GetMapping("/api/v1/bookings")
    @Operation(summary = "List a customer's bookings")
    public PageResponse<BookingResponse> list(
            @RequestParam @NotBlank @Email String customerEmail,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return bookingService.findByCustomer(customerEmail, pageable);
    }
}
