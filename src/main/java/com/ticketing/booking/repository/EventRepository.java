package com.ticketing.booking.repository;

import com.ticketing.booking.domain.Event;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Every filter is optional: a null argument drops that predicate. Keeps one
     * query for the listing endpoint instead of a combinatorial set of methods.
     */
    @Query("""
            select e from Event e
            where (:venue is null or lower(e.venue) like lower(concat('%', :venue, '%')))
              and (:from  is null or e.startsAt >= :from)
              and (:to    is null or e.startsAt <= :to)
            """)
    Page<Event> search(
            @Param("venue") String venue,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable);
}
