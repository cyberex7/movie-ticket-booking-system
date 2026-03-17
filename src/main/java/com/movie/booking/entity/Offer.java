package com.movie.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "offers",
       uniqueConstraints = @UniqueConstraint(columnNames = "offer_code"),
       indexes = {
           @Index(name = "idx_offers_code",   columnList = "offer_code"),
           @Index(name = "idx_offers_active", columnList = "is_active")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long offerId;

    @Column(name = "offer_code", nullable = false, unique = true, length = 50)
    private String offerCode;

    @Column(name = "offer_name", nullable = false, length = 255)
    private String offerName;

    @Column(name = "offer_description", columnDefinition = "TEXT")
    private String offerDescription;

    /**
     * Typed enum for discount category.
     * PERCENTAGE = flat-rate % off total
     * FLAT       = fixed rupee amount off
     * THIRD_TICKET = 50% on every 3rd seat (sorted price-desc)
     * AFTERNOON    = 20% off for shows in the afternoon window
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", length = 50)
    private DiscountType discountType;

    @Column(name = "discount_value", precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** Minimum seats required for this offer to activate. */
    @Column(name = "minimum_seat_count")
    @Builder.Default
    private Integer minimumSeatCount = 1;

    /** When true, this offer stacks with other active offers on the same booking. */
    @Column(name = "is_stackable", nullable = false)
    @Builder.Default
    private Boolean isStackable = false;

    // ── Scope restrictions ── null means the offer is platform-wide ──────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_city_id")
    private City applicableCity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicable_theatre_id")
    private Theatre applicableTheatre;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DiscountType {
        PERCENTAGE, FLAT, THIRD_TICKET, AFTERNOON
    }
}
