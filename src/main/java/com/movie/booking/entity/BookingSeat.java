package com.movie.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Records which seat belongs to which booking, and snapshots the ticket price
 * at the moment of purchase. This means post-booking price changes on the show
 * do not retroactively affect issued receipts.
 */
@Entity
@Table(name = "booking_seats",
       uniqueConstraints = @UniqueConstraint(columnNames = {"booking_id", "seat_id"}),
       indexes = {
           @Index(name = "idx_booking_seats_booking", columnList = "booking_id"),
           @Index(name = "idx_booking_seats_seat",    columnList = "seat_id")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingSeatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    /** Ticket price at the time of booking — immutable receipt snapshot. */
    @Column(name = "ticket_price_at_booking", nullable = false, precision = 10, scale = 2)
    private BigDecimal ticketPriceAtBooking;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
