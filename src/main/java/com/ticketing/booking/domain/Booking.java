package com.ticketing.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** A confirmed sale. Immutable once created, apart from the seats it owns. */
@Entity
@Table(name = "bookings")
public class Booking extends BaseEntity {

    private static final String REFERENCE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int REFERENCE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_reference", nullable = false, unique = true, updatable = false, length = 16)
    private String bookingReference = generateReference();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "customer_email", nullable = false)
    private String customerEmail;

    /** Money is BigDecimal, never double: binary floating point cannot hold 0.10 exactly. */
    @Column(name = "total_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @OneToMany(mappedBy = "booking")
    private List<Seat> seats = new ArrayList<>();

    protected Booking() {
        // for JPA
    }

    public Booking(Event event, String customerEmail) {
        this.event = event;
        this.customerEmail = customerEmail;
    }

    /** Takes ownership of a seat and adds its price to the total. */
    public void addSeat(Seat seat) {
        seats.add(seat);
        seat.attachToBooking(this);
        this.totalPrice = this.totalPrice.add(seat.getPrice());
    }

    /**
     * Ambiguous characters (I, O, 0, 1) are left out of the alphabet so a
     * reference can be read over the phone without confusion.
     */
    private static String generateReference() {
        StringBuilder reference = new StringBuilder(REFERENCE_LENGTH);
        for (int i = 0; i < REFERENCE_LENGTH; i++) {
            reference.append(REFERENCE_ALPHABET.charAt(RANDOM.nextInt(REFERENCE_ALPHABET.length())));
        }
        return reference.toString();
    }

    public Long getId() {
        return id;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public Event getEvent() {
        return event;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public List<Seat> getSeats() {
        return seats;
    }
}
