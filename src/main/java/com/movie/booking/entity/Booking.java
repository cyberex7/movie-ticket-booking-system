package com.movie.booking.entity;

import com.movie.booking.enums.BookingStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bookings",
       uniqueConstraints = @UniqueConstraint(columnNames = "booking_reference"),
       indexes = {
           @Index(name = "idx_bookings_customer",  columnList = "user_id"),
           @Index(name = "idx_bookings_show",      columnList = "show_id"),
           @Index(name = "idx_bookings_reference", columnList = "booking_reference"),
           @Index(name = "idx_bookings_status",    columnList = "booking_status")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long bookingId;

    @Column(name = "booking_reference", unique = true, nullable = false, length = 50)
    private String bookingReference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "show_id", nullable = false)
    private Show show;

    /** Denormalised seat count — avoids iterating the collection just to render a receipt. */
    @Column(name = "number_of_seats", nullable = false)
    private Integer numberOfSeats;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL,
               fetch = FetchType.EAGER, orphanRemoval = true)
    @Builder.Default
    private List<BookingSeat> bookedSeats = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_status", nullable = false, length = 50)
    @Builder.Default
    private BookingStatus bookingStatus = BookingStatus.PENDING;

    /**
     * Payment lifecycle is tracked independently from booking status.
     * A booking can be CONFIRMED while payment is still PENDING
     * (awaiting gateway callback).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 50)
    @Builder.Default
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    @Column(name = "gross_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "discount_amount", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "net_payable_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal netPayableAmount;

    /** Describes which offers were applied, stored as a pipe-separated string. */
    @Column(name = "applied_offer_summary", columnDefinition = "TEXT")
    private String appliedOfferSummary;

    @Column(name = "payment_gateway_reference")
    private String paymentGatewayReference;

    @Column(name = "booking_date")
    private LocalDateTime bookingDate;

    /** Optimistic lock — prevents concurrent modifications to the same booking. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum PaymentStatus {
        PENDING, COMPLETED, FAILED, REFUNDED
    }
}
