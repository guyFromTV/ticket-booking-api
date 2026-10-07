package com.ticketing.booking.repository;

import com.ticketing.booking.domain.Booking;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    Optional<Booking> findByBookingReference(String bookingReference);

    Page<Booking> findByCustomerEmailIgnoreCase(String customerEmail, Pageable pageable);
}
