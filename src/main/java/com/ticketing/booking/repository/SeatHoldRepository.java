package com.ticketing.booking.repository;

import com.ticketing.booking.domain.HoldStatus;
import com.ticketing.booking.domain.SeatHold;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatHoldRepository extends JpaRepository<SeatHold, Long> {

    Optional<SeatHold> findByHoldReference(String holdReference);

    /** Feeds the expiry sweeper. */
    List<SeatHold> findByStatusAndExpiresAtBefore(HoldStatus status, Instant cutoff);
}
