package com.movie.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "movies",
       indexes = {
           @Index(name = "idx_movies_language",     columnList = "language"),
           @Index(name = "idx_movies_genre",        columnList = "genre"),
           @Index(name = "idx_movies_active",       columnList = "is_active"),
           @Index(name = "idx_movies_release_date", columnList = "release_date")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movieId;

    @Column(nullable = false, length = 255)
    private String movieTitle;

    @Column(columnDefinition = "TEXT")
    private String movieDescription;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false, length = 50)
    private String language;

    @Column(nullable = false, length = 100)
    private String genre;

    @Column(name = "release_date")
    private LocalDate releaseDate;

    /** Certification board rating: U, UA, A, S. */
    @Column(length = 10)
    private String ageRating;

    @Column(name = "poster_url", columnDefinition = "TEXT")
    private String posterUrl;

    @Column(name = "trailer_url", columnDefinition = "TEXT")
    private String trailerUrl;

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
}
