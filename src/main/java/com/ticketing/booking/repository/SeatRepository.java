package com.ticketing.booking.repository;

import com.ticketing.booking.domain.Seat;
import com.ticketing.booking.domain.SeatStatus;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * Loads seats under a pessimistic write lock ({@code SELECT ... FOR UPDATE}).
     *
     * <p>This is the single point that makes concurrent booking safe. Two requests
     * asking for the same seat both reach this query; the first takes the row
     * lock, the second blocks until the first commits and then sees the updated
     * status, so it fails the availability check instead of double-selling.
     *
     * <p>The {@code order by s.id} is not cosmetic. Without a deterministic lock
     * order, a request for seats [1,2] and a concurrent request for [2,1] could
     * each hold one row and wait for the other -- a deadlock. Sorting by id means
     * every transaction grabs rows in the same sequence.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id in :ids order by s.id")
    List<Seat> lockAllById(@Param("ids") Collection<Long> ids);

    Page<Seat> findByEventIdOrderById(Long eventId, Pageable pageable);

    Page<Seat> findByEventIdAndStatusOrderById(Long eventId, SeatStatus status, Pageable pageable);

    /** Aggregate count, so the event detail view never loads the seat collection. */
    long countByEventIdAndStatus(Long eventId, SeatStatus status);

    long countByEventId(Long eventId);
}
