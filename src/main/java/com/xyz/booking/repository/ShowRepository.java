package com.xyz.booking.repository;

import com.xyz.booking.entity.Show;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface ShowRepository extends JpaRepository<Show, Long> {

    List<Show> findByScreenScreenIdAndShowDateAndIsActiveTrue(Long screenId, LocalDate showDate);

    List<Show> findByMovieMovieIdAndShowDateAndIsActiveTrue(Long movieId, LocalDate showDate);

    /**
     * Core Read Scenario 1: show timings for a movie across all theatres in a city on a date.
     * Results are ordered by theatre name then show time for a clean listing UI.
     */
    @Query("""
        SELECT s FROM Show s
        JOIN s.screen sc
        JOIN sc.theatre t
        WHERE t.city.cityId = :cityId
          AND s.movie.movieId = :movieId
          AND s.showDate = :showDate
          AND s.isActive = true
        ORDER BY t.theatreName, s.showTime
        """)
    List<Show> findScheduledShowsInCityForMovieOnDate(
            @Param("cityId") Long cityId,
            @Param("movieId") Long movieId,
            @Param("showDate") LocalDate showDate);

    /**
     * Variant using city name string (from customer API request param).
     */
    @Query("""
        SELECT s FROM Show s
        JOIN s.screen sc
        JOIN sc.theatre t
        WHERE LOWER(t.city.cityName) = LOWER(:cityName)
          AND s.movie.movieId = :movieId
          AND s.showDate = :showDate
          AND s.isActive = true
        ORDER BY t.theatreName, s.showTime
        """)
    List<Show> findScheduledShowsInCityByNameForMovieOnDate(
            @Param("cityName") String cityName,
            @Param("movieId") Long movieId,
            @Param("showDate") LocalDate showDate);

    @Query("""
        SELECT s FROM Show s
        JOIN s.screen sc
        WHERE sc.theatre.theatreId = :theatreId
          AND s.showDate = :showDate
          AND s.isActive = true
        ORDER BY s.showTime
        """)
    List<Show> findScheduledShowsByTheatreAndDate(
            @Param("theatreId") Long theatreId,
            @Param("showDate") LocalDate showDate);

    @Query("""
        SELECT s FROM Show s
        JOIN s.screen sc
        WHERE sc.theatre.theatreId = :theatreId
          AND s.movie.movieId = :movieId
          AND s.showDate = :showDate
          AND s.isActive = true
        ORDER BY s.showTime
        """)
    List<Show> findScheduledShowsByTheatreAndMovieAndDate(
            @Param("theatreId") Long theatreId,
            @Param("movieId") Long movieId,
            @Param("showDate") LocalDate showDate);

    /**
     * Overlap guard: returns shows on the same screen/date whose time window
     * intersects with [proposedStartTime, proposedEndTime).
     * Called before persisting a new show to prevent double-scheduling.
     */
    @Query("""
        SELECT s FROM Show s
        WHERE s.screen.screenId = :screenId
          AND s.showDate = :showDate
          AND s.isActive = true
          AND s.showTime < :proposedEndTime
          AND s.endTime > :proposedStartTime
        """)
    List<Show> findConflictingShowsOnScreen(
            @Param("screenId") Long screenId,
            @Param("showDate") LocalDate showDate,
            @Param("proposedStartTime") LocalTime proposedStartTime,
            @Param("proposedEndTime") LocalTime proposedEndTime);

    boolean existsByScreenScreenIdAndShowDateAndShowTime(
            Long screenId, LocalDate showDate, LocalTime showTime);
}
