package com.movie.booking.entity;

import com.movie.booking.enums.ShowTimeSlot;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "shows",
       uniqueConstraints = @UniqueConstraint(columnNames = {"screen_id", "show_date", "show_time"}),
       indexes = {
           @Index(name = "idx_shows_movie",      columnList = "movie_id"),
           @Index(name = "idx_shows_screen",     columnList = "screen_id"),
           @Index(name = "idx_shows_date",       columnList = "show_date"),
           @Index(name = "idx_shows_status",     columnList = "show_status"),
           @Index(name = "idx_shows_movie_date", columnList = "movie_id, show_date")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Show {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long showId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movie_id", nullable = false)
    private Movie movie;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private Screen screen;

    @Column(name = "show_date", nullable = false)
    private LocalDate showDate;

    @Column(name = "show_time", nullable = false)
    private LocalTime showTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", length = 20)
    private ShowTimeSlot timeSlot;

    @Column(name = "base_ticket_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseTicketPrice;

    @Column(name = "available_seat_count", nullable = false)
    @Builder.Default
    private Integer availableSeatCount = 0;

    /**
     * Lifecycle state machine:
     * SCHEDULED → RUNNING → COMPLETED
     *           ↘ CANCELLED (from any state)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "show_status", length = 50)
    @Builder.Default
    private ShowStatus showStatus = ShowStatus.SCHEDULED;

    /** Soft-delete flag used in query filters. */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Optimistic lock — prevents lost-update on concurrent seat-count decrements. */
    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ShowStatus {
        SCHEDULED, RUNNING, COMPLETED, CANCELLED
    }
}
