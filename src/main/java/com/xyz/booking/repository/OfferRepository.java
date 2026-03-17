package com.xyz.booking.repository;

import com.xyz.booking.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByOfferCode(String offerCode);

    /**
     * All offers currently valid and active — used in admin listings.
     */
    @Query("""
        SELECT o FROM Offer o
        WHERE o.isActive = true
          AND (o.validFrom IS NULL OR o.validFrom <= :now)
          AND (o.validUntil IS NULL OR o.validUntil >= :now)
        ORDER BY o.offerName
        """)
    List<Offer> findCurrentlyActiveOffers(@Param("now") LocalDateTime now);

    /**
     * Active offers scoped to a specific city and theatre combination,
     * plus platform-wide offers (no city/theatre restriction).
     */
    @Query("""
        SELECT o FROM Offer o
        WHERE o.isActive = true
          AND (o.validFrom IS NULL OR o.validFrom <= :now)
          AND (o.validUntil IS NULL OR o.validUntil >= :now)
          AND (o.applicableCity IS NULL OR o.applicableCity.cityId = :cityId)
          AND (o.applicableTheatre IS NULL OR o.applicableTheatre.theatreId = :theatreId)
        ORDER BY o.offerName
        """)
    List<Offer> findActiveOffersForCityAndTheatre(
            @Param("cityId") Long cityId,
            @Param("theatreId") Long theatreId,
            @Param("now") LocalDateTime now);
}
