package com.ticketing.booking.domain;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.math.BigDecimal;

/**
 * A single sellable seat. This is the contended row in the system: every booking
 * attempt competes for it, so it carries both guards.
 *
 * <p>Two independent protections keep a seat from being sold twice:
 * <ul>
 *   <li>a {@link Version} column, so a lost update is detected even on a path
 *       that did not take a lock;</li>
 *   <li>a pessimistic {@code SELECT ... FOR UPDATE} taken by the hold service,
 *       which serialises competing requests instead of making them retry.</li>
 * </ul>
 */
@Entity
@Table(
        name = "seats",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_seat_position",
                columnNames = {"event_id", "section", "row_label", "seat_number"}))
public class Seat extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private int seatNumber;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SeatStatus status = SeatStatus.AVAILABLE;

    /** Set while the seat sits in an active hold, cleared when the hold ends. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hold_id")
    private SeatHold hold;

    /** Set once the seat is sold. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Version
    private Long version;

    protected Seat() {
        // for JPA
    }

    public Seat(String section, String rowLabel, int seatNumber, BigDecimal price) {
        this.section = section;
        this.rowLabel = rowLabel;
        this.seatNumber = seatNumber;
        this.price = price;
    }

    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    void attachToHold(SeatHold newHold) {
        this.hold = newHold;
        this.status = SeatStatus.HELD;
    }

    void releaseToPool() {
        this.hold = null;
        this.status = SeatStatus.AVAILABLE;
    }

    void attachToBooking(Booking newBooking) {
        this.booking = newBooking;
        this.hold = null;
        this.status = SeatStatus.BOOKED;
    }

    /** Human-readable position, e.g. {@code Stalls-A-12}. */
    public String label() {
        return "%s-%s-%d".formatted(section, rowLabel, seatNumber);
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    void setEvent(Event event) {
        this.event = event;
    }

    public String getSection() {
        return section;
    }

    public String getRowLabel() {
        return rowLabel;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public SeatHold getHold() {
        return hold;
    }

    public Booking getBooking() {
        return booking;
    }

    public Long getVersion() {
        return version;
    }
}
