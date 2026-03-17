package com.movie.booking.repository;

import com.movie.booking.entity.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TheatreRepository extends JpaRepository<Theatre, Long> {

    List<Theatre> findByCityCityIdAndIsActiveTrue(Long cityId);

    List<Theatre> findByTheatrePartnerUserIdAndIsActiveTrue(Long partnerUserId);

    /**
     * Find every active theatre in a city that has at least one scheduled show
     * for the given movie on the given date — the core "Read Scenario 1" query.
     */
    @Query("""
        SELECT DISTINCT t FROM Theatre t
        JOIN t.screens sc
        JOIN Show s ON s.screen = sc
        WHERE t.city.cityId = :cityId
          AND s.movie.movieId = :movieId
          AND s.showDate = :showDate
          AND s.isActive = true
          AND t.isActive = true
        ORDER BY t.theatreName
        """)
    List<Theatre> findTheatresWithScheduledShowsForMovieOnDate(
            @Param("cityId") Long cityId,
            @Param("movieId") Long movieId,
            @Param("showDate") LocalDate showDate);

    /**
     * Find theatres by city name string — used by CustomerController
     * which receives city as a request param (not an ID).
     */
    @Query("""
        SELECT DISTINCT t FROM Theatre t
        JOIN t.screens sc
        JOIN Show s ON s.screen = sc
        WHERE LOWER(t.city.cityName) = LOWER(:cityName)
          AND s.movie.movieId = :movieId
          AND t.isActive = true
        ORDER BY t.theatreName
        """)
    List<Theatre> findTheatresShowingMovieInCityByName(
            @Param("movieId") Long movieId,
            @Param("cityName") String cityName);
}
