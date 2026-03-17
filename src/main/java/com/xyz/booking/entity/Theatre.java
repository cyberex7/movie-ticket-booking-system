package com.xyz.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "theatres",
       indexes = {
           @Index(name = "idx_theatres_city",    columnList = "city_id"),
           @Index(name = "idx_theatres_partner", columnList = "partner_id"),
           @Index(name = "idx_theatres_active",  columnList = "is_active")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Theatre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long theatreId;

    @Column(nullable = false, length = 255)
    private String theatreName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private User theatrePartner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id", nullable = false)
    private City city;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String streetAddress;

    @Column(length = 100)
    private String stateName;

    @Column(length = 10)
    private String pincode;

    /** GPS latitude for map integrations and location-based search. */
    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    /** GPS longitude for map integrations and location-based search. */
    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(length = 20)
    private String contactPhone;

    @Column(length = 255)
    private String contactEmail;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Version
    private Long version;

    @OneToMany(mappedBy = "theatre", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Screen> screens;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
