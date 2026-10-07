package com.ticketing.booking.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A temporary claim on one or more seats. Clients get a hold first, then confirm
 * it into a {@link Booking}; anything not confirmed before {@code expiresAt} is
 * swept back into the available pool.
 */
@Entity
@Table(name = "seat_holds")
public class SeatHold extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Public identifier. Clients never see the numeric primary key. */
    @Column(name = "hold_reference", nullable = false, unique = true, updatable = false, length = 36)
    private String holdReference = UUID.randomUUID().toString();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private HoldStatus status = HoldStatus.ACTIVE;

    @OneToMany(mappedBy = "hold", cascade = CascadeType.PERSIST)
    private List<Seat> seats = new ArrayList<>();

    protected SeatHold() {
        // for JPA
    }

    public SeatHold(Event event, String customerEmail, Instant expiresAt) {
        this.event = event;
        this.customerEmail = customerEmail;
        this.expiresAt = expiresAt;
    }

    /** Claims a seat for this hold and flips it to {@code HELD}. */
    public void claim(Seat seat) {
        seats.add(seat);
        seat.attachToHold(this);
    }

    /**
     * A hold is only usable while it is {@code ACTIVE} and still inside its
     * window. The clock is checked here rather than trusting the sweeper, so a
     * hold that expired a second ago cannot be confirmed just because the
     * background job has not run yet.
     */
    public boolean isUsableAt(Instant now) {
        return status == HoldStatus.ACTIVE && now.isBefore(expiresAt);
    }

    public boolean hasExpiredBy(Instant now) {
        return status == HoldStatus.ACTIVE && !now.isBefore(expiresAt);
    }

    public void release(HoldStatus terminalStatus) {
        seats.forEach(Seat::releaseToPool);
        seats.clear();
        this.status = terminalStatus;
    }

    public void markConfirmed() {
        this.status = HoldStatus.CONFIRMED;
    }

    public BigDecimal totalPrice() {
        return seats.stream().map(Seat::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public String getHoldReference() {
        return holdReference;
    }

    public Event getEvent() {
        return event;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public HoldStatus getStatus() {
        return status;
    }

    public List<Seat> getSeats() {
        return seats;
    }
}
