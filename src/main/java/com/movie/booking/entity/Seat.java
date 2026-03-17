package com.movie.booking.entity;

import com.movie.booking.enums.SeatStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "seats",
       indexes = {
           @Index(name = "idx_seats_screen",        columnList = "screen_id"),
           @Index(name = "idx_seats_status",        columnList = "seat_status"),
           @Index(name = "idx_seats_screen_status", columnList = "screen_id, seat_status")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;          // e.g. A1, B5

    @Column(name = "row_label", nullable = false, length = 5)
    private String rowLabel;            // e.g. A, B, C

    @Column(name = "column_number", nullable = false)
    private Integer columnNumber;

    /**
     * Seat category drives the price multiplier at allocation time:
     * REGULAR = 1.0×, PREMIUM = 1.5×, VIP = 2.0×.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", length = 50, nullable = false)
    @Builder.Default
    private SeatType seatType = SeatType.REGULAR;

    /** Per-seat ticket price, frozen at allocation time based on seatType multiplier. */
    @Column(name = "ticket_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal ticketPrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_status", length = 50, nullable = false)
    @Builder.Default
    private SeatStatus seatStatus = SeatStatus.AVAILABLE;

    /** Timestamp when this seat was reserved (for future hold-expiry feature). */
    @Column(name = "reserved_at")
    private LocalDateTime reservedAt;

    /** Deadline by which the pending reservation must be confirmed. */
    @Column(name = "reserved_until")
    private LocalDateTime reservedUntil;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum SeatType {
        REGULAR, PREMIUM, VIP;

        /** Price multiplier relative to the show's base ticket price. */
        public BigDecimal priceMultiplier() {
            return switch (this) {
                case VIP     -> BigDecimal.valueOf(2.0);
                case PREMIUM -> BigDecimal.valueOf(1.5);
                default      -> BigDecimal.ONE;
            };
        }
    }
}
